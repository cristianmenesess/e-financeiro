package com.efinanceiro.servico;

import com.efinanceiro.dominio.AlcanceEdicao;
import com.efinanceiro.dominio.Cartao;
import com.efinanceiro.dominio.Categoria;
import com.efinanceiro.dominio.Conta;
import com.efinanceiro.dominio.Recorrencia;
import com.efinanceiro.dominio.TipoTransacao;
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
     * (uma por mês, a partir da data de início), cada uma com o seu número de parcela gravado. Com
     * cartão, a data de início é a da compra e cada parcela cai no vencimento de uma fatura.
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

        Recorrencia recorrencia = gerarRecorrencia(usuario, conta, cartao, categoria, requisicao.descricao(), requisicao.valor(),
                requisicao.tipo(), requisicao.totalParcelas(), dataInicio);

        return paraResposta(recorrencia, contarParcelasRestantes(recorrencia));
    }

    /**
     * Cria uma recorrência com todas as parcelas mensais a partir da data de início. Quem chama já
     * validou os dados e resolveu conta, cartão e categoria.
     *
     * @param usuario Dono da recorrência
     * @param conta Conta das parcelas
     * @param cartao Cartão das parcelas, ou null
     * @param categoria Categoria (do mesmo tipo)
     * @param descricao Descrição
     * @param valor Valor de cada parcela
     * @param tipo Entrada ou saída
     * @param totalParcelas Quantidade de parcelas
     * @param dataInicio Data da primeira parcela; com cartão, a data da compra (cada parcela cai no
     *                   vencimento de uma fatura, a partir da fatura da compra)
     * @return Recorrência criada
     */
    public Recorrencia gerarRecorrencia(Usuario usuario, Conta conta, Cartao cartao, Categoria categoria, String descricao,
                                        BigDecimal valor, TipoTransacao tipo, int totalParcelas, LocalDate dataInicio) {
        Recorrencia recorrencia = new Recorrencia();
        recorrencia.setUsuario(usuario);
        recorrencia.setDescricao(descricao);
        recorrencia.setValor(valor);
        recorrencia.setTipo(tipo);
        recorrencia.setCategoria(categoria);
        recorrencia.setConta(conta);
        recorrencia.setCartao(cartao);
        recorrencia.setTotalParcelas(totalParcelas);
        recorrencia.setDataInicio(dataInicio);
        repositorioRecorrencia.save(recorrencia);

        gerarParcelas(recorrencia, 1);
        return recorrencia;
    }

    /**
     * Edita qualquer dado de uma recorrência. Com alcance FUTURAS (padrão), as parcelas até hoje
     * ficam como estão e as seguintes são refeitas com os dados novos, continuando a numeração;
     * com TODAS, todas as parcelas são refeitas. O total de parcelas não pode ficar menor que as
     * parcelas mantidas.
     *
     * @param emailUsuario E-mail do usuário autenticado
     * @param id Id da recorrência
     * @param requisicao Novos dados e o alcance da edição
     * @return Recorrência atualizada
     */
    public RespostaRecorrencia atualizarRecorrencia(String emailUsuario, Long id, RequisicaoRecorrencia requisicao) {
        Usuario usuario = buscadorRecursosDoUsuario.buscarUsuario(emailUsuario);
        Recorrencia recorrencia = repositorioRecorrencia.findByIdAndUsuarioId(id, usuario.getId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Recorrência não encontrada"));

        Categoria categoria = buscadorRecursosDoUsuario.buscarCategoria(requisicao.categoriaId(), usuario.getId());

        if (categoria.getTipo() != requisicao.tipo()) {
            throw new DadosInvalidosException("A categoria não é do mesmo tipo da movimentação");
        }

        LocalDate hoje = LocalDate.now(relogio);
        List<Transacao> parcelas = repositorioTransacao.findByRecorrenciaId(recorrencia.getId());
        List<Transacao> refeitas = requisicao.alcance() == AlcanceEdicao.TODAS ? parcelas
                : parcelas.stream().filter(parcela -> parcela.getDataTransacao().isAfter(hoje)).toList();

        int ultimaMantida = parcelas.stream()
                .filter(parcela -> !refeitas.contains(parcela))
                .mapToInt(parcela -> parcela.getNumeroParcela() != null ? parcela.getNumeroParcela() : 0)
                .max().orElse(0);

        if (requisicao.totalParcelas() < ultimaMantida) {
            throw new DadosInvalidosException("Já passaram " + ultimaMantida + " parcelas; o total não pode ser menor que isso");
        }

        repositorioTransacao.deleteAll(refeitas);

        recorrencia.setDescricao(requisicao.descricao());
        recorrencia.setValor(requisicao.valor());
        recorrencia.setTipo(requisicao.tipo());
        recorrencia.setCategoria(categoria);
        recorrencia.setConta(buscadorRecursosDoUsuario.buscarConta(requisicao.contaId(), usuario.getId()));
        recorrencia.setCartao(buscadorRecursosDoUsuario.buscarCartaoOpcional(requisicao.cartaoId(), usuario.getId()));
        recorrencia.setTotalParcelas(requisicao.totalParcelas());

        if (requisicao.dataInicio() != null) {
            recorrencia.setDataInicio(requisicao.dataInicio());
        }

        repositorioRecorrencia.save(recorrencia);
        gerarParcelas(recorrencia, ultimaMantida + 1);

        return paraResposta(recorrencia, contarParcelasRestantes(recorrencia));
    }

    // Gera as parcelas da recorrência a partir do número informado até o total
    private void gerarParcelas(Recorrencia recorrencia, int primeiraParcela) {
        List<Transacao> parcelas = new ArrayList<>();

        for (int numero = primeiraParcela; numero <= recorrencia.getTotalParcelas(); numero++) {
            Transacao transacao = new Transacao();
            transacao.setUsuario(recorrencia.getUsuario());
            transacao.setDescricao(recorrencia.getDescricao());
            transacao.setValor(recorrencia.getValor());
            transacao.setTipo(recorrencia.getTipo());
            transacao.setCategoria(recorrencia.getCategoria());
            transacao.setConta(recorrencia.getConta());
            transacao.setCartao(recorrencia.getCartao());
            transacao.setRecorrencia(recorrencia);
            transacao.setNumeroParcela(numero);

            if (recorrencia.getCartao() != null) {
                CalculadoraFatura.aplicarData(transacao, recorrencia.getDataInicio());
            } else {
                transacao.setDataTransacao(recorrencia.getDataInicio().plusMonths(numero - 1L));
            }

            parcelas.add(transacao);
        }

        repositorioTransacao.saveAll(parcelas);
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
