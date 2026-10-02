package com.efinanceiro.servico;

import com.efinanceiro.dominio.Cartao;
import com.efinanceiro.dominio.TipoTransacao;
import com.efinanceiro.dominio.Transacao;
import com.efinanceiro.dominio.Usuario;
import com.efinanceiro.dto.requisicao.RequisicaoCartao;
import com.efinanceiro.dto.resposta.RespostaCartao;
import com.efinanceiro.repositorio.RepositorioCartao;
import com.efinanceiro.repositorio.RepositorioTransacao;
import com.efinanceiro.repositorio.projecao.GastoPorCartao;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Transactional
public class ServicoCartao {

    private final RepositorioCartao repositorioCartao;
    private final RepositorioTransacao repositorioTransacao;
    private final BuscadorRecursosDoUsuario buscadorRecursosDoUsuario;
    private final Clock relogio;

    public ServicoCartao(RepositorioCartao repositorioCartao,
                          RepositorioTransacao repositorioTransacao,
                          BuscadorRecursosDoUsuario buscadorRecursosDoUsuario,
                          Clock relogio) {
        this.repositorioCartao = repositorioCartao;
        this.repositorioTransacao = repositorioTransacao;
        this.buscadorRecursosDoUsuario = buscadorRecursosDoUsuario;
        this.relogio = relogio;
    }

    /**
     * Lista os cartões do usuário autenticado, cada um com o total da fatura atual (a que ainda
     * vai fechar). Os gastos de todos os cartões saem de uma única consulta agregada no banco.
     *
     * @param emailUsuario E-mail do usuário autenticado
     * @return Lista de cartões com a respectiva fatura atual
     */
    @Transactional(readOnly = true)
    public List<RespostaCartao> listarCartoes(String emailUsuario) {
        Usuario usuario = buscadorRecursosDoUsuario.buscarUsuario(emailUsuario);
        return paraRespostas(usuario.getId(), repositorioCartao.findByUsuarioId(usuario.getId()));
    }

    /**
     * Cria um novo cartão para o usuário autenticado.
     *
     * @param emailUsuario E-mail do usuário autenticado
     * @param requisicao Dados do cartão (nome, cores, fechamento e vencimento)
     * @return Cartão criado
     */
    public RespostaCartao criarCartao(String emailUsuario, RequisicaoCartao requisicao) {
        Usuario usuario = buscadorRecursosDoUsuario.buscarUsuario(emailUsuario);

        Cartao cartao = new Cartao();
        cartao.setUsuario(usuario);
        preencherCartao(cartao, requisicao);

        repositorioCartao.save(cartao);
        return paraRespostas(usuario.getId(), List.of(cartao)).get(0);
    }

    /**
     * Atualiza um cartão do usuário autenticado. Se o fechamento ou o vencimento mudar, as
     * transações futuras do cartão passam para o vencimento da nova fatura; as passadas não mudam.
     *
     * @param emailUsuario E-mail do usuário autenticado
     * @param id Id do cartão a atualizar
     * @param requisicao Novos dados do cartão
     * @return Cartão atualizado
     */
    public RespostaCartao atualizarCartao(String emailUsuario, Long id, RequisicaoCartao requisicao) {
        Usuario usuario = buscadorRecursosDoUsuario.buscarUsuario(emailUsuario);
        Cartao cartao = buscadorRecursosDoUsuario.buscarCartao(id, usuario.getId());

        boolean cicloMudou = !Objects.equals(cartao.getDiaFechamento(), requisicao.diaFechamento())
                || !Objects.equals(cartao.getDiaVencimento(), requisicao.diaVencimento());

        preencherCartao(cartao, requisicao);
        repositorioCartao.save(cartao);

        if (cicloMudou) {
            recalcularFaturasFuturas(cartao);
        }

        return paraRespostas(usuario.getId(), List.of(cartao)).get(0);
    }

    /**
     * Exclui um cartão do usuário autenticado. As transações do cartão continuam, sem cartão
     * (a FK no banco é ON DELETE SET NULL).
     *
     * @param emailUsuario E-mail do usuário autenticado
     * @param id Id do cartão a excluir
     */
    public void excluirCartao(String emailUsuario, Long id) {
        Usuario usuario = buscadorRecursosDoUsuario.buscarUsuario(emailUsuario);
        Cartao cartao = buscadorRecursosDoUsuario.buscarCartao(id, usuario.getId());
        repositorioCartao.delete(cartao);
    }

    private void preencherCartao(Cartao cartao, RequisicaoCartao requisicao) {
        cartao.setNome(requisicao.nome());
        cartao.setCorFundo(requisicao.corFundo());
        cartao.setCorTexto(requisicao.corTexto());
        cartao.setDiaFechamento(requisicao.diaFechamento());
        cartao.setDiaVencimento(requisicao.diaVencimento());
    }

    private void recalcularFaturasFuturas(Cartao cartao) {
        List<Transacao> futuras = repositorioTransacao.findByCartaoIdAndDataTransacaoGreaterThan(cartao.getId(), LocalDate.now(relogio));

        futuras.stream()
                .filter(transacao -> transacao.getDataCompra() != null)
                .forEach(transacao -> CalculadoraFatura.aplicarData(transacao, transacao.getDataCompra()));

        repositorioTransacao.saveAll(futuras);
    }

    private List<RespostaCartao> paraRespostas(Long usuarioId, List<Cartao> cartoes) {
        if (cartoes.isEmpty()) {
            return List.of();
        }

        LocalDate hoje = LocalDate.now(relogio);

        // Fatura atual = a que recebe uma compra feita hoje; vai do dia seguinte ao vencimento anterior até o vencimento dela
        Map<Long, LocalDate> vencimentos = cartoes.stream()
                .collect(Collectors.toMap(Cartao::getId, cartao -> CalculadoraFatura.vencimentoDaParcela(cartao, hoje, 1)));

        LocalDate inicio = cartoes.stream()
                .map(cartao -> CalculadoraFatura.vencimentoAnterior(cartao, vencimentos.get(cartao.getId())).plusDays(1))
                .min(Comparator.naturalOrder()).orElseThrow();
        LocalDate fim = vencimentos.values().stream().max(Comparator.naturalOrder()).orElseThrow();

        List<GastoPorCartao> gastos = repositorioTransacao.somarPorCartaoEDia(usuarioId, TipoTransacao.SAIDA, inicio, fim);

        return cartoes.stream()
                .map(cartao -> {
                    LocalDate vencimento = vencimentos.get(cartao.getId());
                    LocalDate vencimentoAnterior = CalculadoraFatura.vencimentoAnterior(cartao, vencimento);

                    BigDecimal fatura = gastos.stream()
                            .filter(gasto -> gasto.cartaoId().equals(cartao.getId()))
                            .filter(gasto -> gasto.data().isAfter(vencimentoAnterior) && !gasto.data().isAfter(vencimento))
                            .map(GastoPorCartao::total)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    return new RespostaCartao(cartao.getId(), cartao.getNome(), cartao.getCorFundo(), cartao.getCorTexto(),
                            cartao.getDiaFechamento(), cartao.getDiaVencimento(), fatura, vencimento);
                })
                .toList();
    }
}
