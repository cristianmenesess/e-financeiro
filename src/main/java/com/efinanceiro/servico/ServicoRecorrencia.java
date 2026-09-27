package com.efinanceiro.servico;

import com.efinanceiro.dominio.Cartao;
import com.efinanceiro.dominio.Categoria;
import com.efinanceiro.dominio.Conta;
import com.efinanceiro.dominio.Recorrencia;
import com.efinanceiro.dominio.Transacao;
import com.efinanceiro.dominio.Usuario;
import com.efinanceiro.dto.requisicao.RequisicaoRecorrencia;
import com.efinanceiro.dto.resposta.RespostaRecorrencia;
import com.efinanceiro.excecao.DadosInvalidosException;
import com.efinanceiro.excecao.RecursoNaoEncontradoException;
import com.efinanceiro.repositorio.RepositorioRecorrencia;
import com.efinanceiro.repositorio.RepositorioTransacao;
import com.efinanceiro.repositorio.projecao.ParcelasPorRecorrencia;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
public class ServicoRecorrencia {

    private final RepositorioRecorrencia repositorioRecorrencia;
    private final RepositorioTransacao repositorioTransacao;
    private final BuscadorRecursosDoUsuario buscadorRecursosDoUsuario;
    private final Clock relogio;

    public ServicoRecorrencia(RepositorioRecorrencia repositorioRecorrencia,
                               RepositorioTransacao repositorioTransacao,
                               BuscadorRecursosDoUsuario buscadorRecursosDoUsuario,
                               Clock relogio) {
        this.repositorioRecorrencia = repositorioRecorrencia;
        this.repositorioTransacao = repositorioTransacao;
        this.buscadorRecursosDoUsuario = buscadorRecursosDoUsuario;
        this.relogio = relogio;
    }

    /**
     * Lista as recorrências do usuário autenticado, cada uma com a quantidade de parcelas
     * futuras (depois de hoje) ainda restantes. As contagens de todas as recorrências saem de uma
     * única consulta agregada.
     *
     * @param emailUsuario E-mail do usuário autenticado
     * @return Lista de recorrências do usuário
     */
    @Transactional(readOnly = true)
    public List<RespostaRecorrencia> listarRecorrencias(String emailUsuario) {
        Usuario usuario = buscadorRecursosDoUsuario.buscarUsuario(emailUsuario);

        Map<Long, Long> restantesPorRecorrencia = repositorioTransacao
                .contarParcelasFuturasPorRecorrencia(usuario.getId(), LocalDate.now(relogio))
                .stream()
                .collect(Collectors.toMap(ParcelasPorRecorrencia::recorrenciaId, ParcelasPorRecorrencia::quantidade));

        return repositorioRecorrencia.findByUsuarioId(usuario.getId()).stream()
                .map(recorrencia -> paraResposta(recorrencia, restantesPorRecorrencia.getOrDefault(recorrencia.getId(), 0L)))
                .toList();
    }

    /**
     * Cria uma nova recorrência para o usuário autenticado e já gera todas as transações
     * (uma por mês, a partir da data de início), cada uma com o seu número de parcela gravado.
     *
     * @param emailUsuario E-mail do usuário autenticado
     * @param requisicao Dados da recorrência
     * @return Recorrência criada
     */
    public RespostaRecorrencia criarRecorrencia(String emailUsuario, RequisicaoRecorrencia requisicao) {
        Usuario usuario = buscadorRecursosDoUsuario.buscarUsuario(emailUsuario);
        Conta conta = buscadorRecursosDoUsuario.buscarConta(requisicao.contaId(), usuario.getId());
        Cartao cartao = buscadorRecursosDoUsuario.buscarCartaoOpcional(requisicao.cartaoId(), usuario.getId());
        LocalDate dataInicio = requisicao.dataInicio() != null ? requisicao.dataInicio() : LocalDate.now(relogio);

        Categoria categoria = buscadorRecursosDoUsuario.buscarCategoria(requisicao.categoriaId(), usuario.getId());

        if (categoria.getTipo() != requisicao.tipo()) {
            throw new DadosInvalidosException("A categoria não é do mesmo tipo da movimentação");
        }

        Recorrencia recorrencia = new Recorrencia();
        recorrencia.setUsuario(usuario);
        recorrencia.setDescricao(requisicao.descricao());
        recorrencia.setValor(requisicao.valor());
        recorrencia.setTipo(requisicao.tipo());
        recorrencia.setCategoria(categoria);
        recorrencia.setConta(conta);
        recorrencia.setCartao(cartao);
        recorrencia.setTotalParcelas(requisicao.totalParcelas());
        recorrencia.setDataInicio(dataInicio);
        repositorioRecorrencia.save(recorrencia);

        List<Transacao> parcelas = new ArrayList<>();

        for (int i = 0; i < requisicao.totalParcelas(); i++) {
            Transacao transacao = new Transacao();
            transacao.setUsuario(usuario);
            transacao.setDescricao(requisicao.descricao());
            transacao.setValor(requisicao.valor());
            transacao.setTipo(requisicao.tipo());
            transacao.setCategoria(categoria);
            transacao.setConta(conta);
            transacao.setCartao(cartao);
            transacao.setRecorrencia(recorrencia);
            transacao.setNumeroParcela(i + 1);
            transacao.setDataTransacao(dataInicio.plusMonths(i));
            parcelas.add(transacao);
        }

        repositorioTransacao.saveAll(parcelas);

        return paraResposta(recorrencia, contarParcelasRestantes(recorrencia));
    }

    /**
     * Exclui uma recorrência junto com todas as parcelas geradas por ela, passadas e futuras.
     *
     * @param emailUsuario E-mail do usuário autenticado
     * @param id Id da recorrência
     */
    public void excluirRecorrencia(String emailUsuario, Long id) {
        Recorrencia recorrencia = buscarRecorrenciaDoUsuario(emailUsuario, id);

        repositorioTransacao.deleteByRecorrenciaId(recorrencia.getId());
        repositorioRecorrencia.delete(recorrencia);
    }

    /**
     * Atualiza o valor das parcelas futuras de uma recorrência (depois de hoje) e o valor
     * de referência da recorrência. As parcelas passadas e a de hoje não mudam — a de hoje
     * já conta no saldo como ocorrida.
     *
     * @param emailUsuario E-mail do usuário autenticado
     * @param id Id da recorrência
     * @param novoValor Novo valor a aplicar nas parcelas futuras
     * @return Recorrência atualizada
     */
    public RespostaRecorrencia atualizarValorFuturo(String emailUsuario, Long id, BigDecimal novoValor) {
        Recorrencia recorrencia = buscarRecorrenciaDoUsuario(emailUsuario, id);

        List<Transacao> futuras = repositorioTransacao
                .findByRecorrenciaIdAndDataTransacaoGreaterThan(recorrencia.getId(), LocalDate.now(relogio));

        if (!futuras.isEmpty()) {
            futuras.forEach(transacao -> transacao.setValor(novoValor));
            repositorioTransacao.saveAll(futuras);

            recorrencia.setValor(novoValor);
            repositorioRecorrencia.save(recorrencia);
        }

        return paraResposta(recorrencia, futuras.size());
    }

    private Recorrencia buscarRecorrenciaDoUsuario(String emailUsuario, Long id) {
        Usuario usuario = buscadorRecursosDoUsuario.buscarUsuario(emailUsuario);

        return repositorioRecorrencia.findByIdAndUsuarioId(id, usuario.getId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Recorrência não encontrada"));
    }

    private long contarParcelasRestantes(Recorrencia recorrencia) {
        return repositorioTransacao.countByRecorrenciaIdAndDataTransacaoGreaterThan(recorrencia.getId(), LocalDate.now(relogio));
    }

    private RespostaRecorrencia paraResposta(Recorrencia recorrencia, long parcelasRestantes) {
        Cartao cartao = recorrencia.getCartao();

        return new RespostaRecorrencia(
                recorrencia.getId(),
                recorrencia.getDescricao(),
                recorrencia.getValor(),
                recorrencia.getTipo(),
                recorrencia.getCategoria().getId(),
                recorrencia.getCategoria().getNome(),
                recorrencia.getConta().getId(),
                recorrencia.getConta().getNome(),
                cartao != null ? cartao.getId() : null,
                cartao != null ? cartao.getNome() : null,
                recorrencia.getTotalParcelas(),
                (int) parcelasRestantes,
                recorrencia.getDataInicio()
        );
    }
}
