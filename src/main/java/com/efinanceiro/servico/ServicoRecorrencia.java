package com.efinanceiro.servico;

import com.efinanceiro.dominio.Cartao;
import com.efinanceiro.dominio.Conta;
import com.efinanceiro.dominio.Recorrencia;
import com.efinanceiro.dominio.Transacao;
import com.efinanceiro.dominio.Usuario;
import com.efinanceiro.dto.requisicao.RequisicaoRecorrencia;
import com.efinanceiro.dto.resposta.RespostaRecorrencia;
import com.efinanceiro.excecao.RecursoNaoEncontradoException;
import com.efinanceiro.repositorio.RepositorioCartao;
import com.efinanceiro.repositorio.RepositorioConta;
import com.efinanceiro.repositorio.RepositorioRecorrencia;
import com.efinanceiro.repositorio.RepositorioTransacao;
import com.efinanceiro.repositorio.RepositorioUsuario;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@Transactional
public class ServicoRecorrencia {

    private final RepositorioRecorrencia repositorioRecorrencia;
    private final RepositorioTransacao repositorioTransacao;
    private final RepositorioUsuario repositorioUsuario;
    private final RepositorioConta repositorioConta;
    private final RepositorioCartao repositorioCartao;

    public ServicoRecorrencia(RepositorioRecorrencia repositorioRecorrencia,
                               RepositorioTransacao repositorioTransacao,
                               RepositorioUsuario repositorioUsuario,
                               RepositorioConta repositorioConta,
                               RepositorioCartao repositorioCartao) {
        this.repositorioRecorrencia = repositorioRecorrencia;
        this.repositorioTransacao = repositorioTransacao;
        this.repositorioUsuario = repositorioUsuario;
        this.repositorioConta = repositorioConta;
        this.repositorioCartao = repositorioCartao;
    }

    /**
     * Lista as recorrências do usuário autenticado, cada uma com a quantidade de parcelas
     * futuras ainda restantes.
     *
     * @param emailUsuario E-mail do usuário autenticado
     * @return Lista de recorrências do usuário
     */
    @Transactional(readOnly = true)
    public List<RespostaRecorrencia> listarRecorrencias(String emailUsuario) {
        Usuario usuario = buscarUsuario(emailUsuario);

        return repositorioRecorrencia.findByUsuarioId(usuario.getId()).stream()
                .map(this::paraResposta)
                .toList();
    }

    /**
     * Cria uma nova recorrência para o usuário autenticado e já gera todas as transações
     * (uma por mês, a partir da data de início).
     *
     * @param emailUsuario E-mail do usuário autenticado
     * @param requisicao Dados da recorrência
     * @return Recorrência criada
     */
    public RespostaRecorrencia criarRecorrencia(String emailUsuario, RequisicaoRecorrencia requisicao) {
        Usuario usuario = buscarUsuario(emailUsuario);
        Conta conta = resolverConta(requisicao.contaId(), usuario.getId());
        Cartao cartao = resolverCartao(requisicao.cartaoId(), usuario.getId());
        LocalDate dataInicio = requisicao.dataInicio() != null ? requisicao.dataInicio() : LocalDate.now();

        Recorrencia recorrencia = new Recorrencia();
        recorrencia.setUsuario(usuario);
        recorrencia.setDescricao(requisicao.descricao());
        recorrencia.setValor(requisicao.valor());
        recorrencia.setTipo(requisicao.tipo());
        recorrencia.setCategoria(requisicao.categoria());
        recorrencia.setConta(conta);
        recorrencia.setCartao(cartao);
        recorrencia.setTotalParcelas(requisicao.totalParcelas());
        recorrencia.setDataInicio(dataInicio);
        repositorioRecorrencia.save(recorrencia);

        for (int i = 0; i < requisicao.totalParcelas(); i++) {
            Transacao transacao = new Transacao();
            transacao.setUsuario(usuario);
            transacao.setDescricao(requisicao.descricao());
            transacao.setValor(requisicao.valor());
            transacao.setTipo(requisicao.tipo());
            transacao.setCategoria(requisicao.categoria());
            transacao.setConta(conta);
            transacao.setCartao(cartao);
            transacao.setRecorrencia(recorrencia);
            transacao.setDataTransacao(dataInicio.plusMonths(i));
            repositorioTransacao.save(transacao);
        }

        return paraResposta(recorrencia);
    }

    /**
     * Cancela as parcelas futuras de uma recorrência (apaga as transações com data de hoje
     * em diante). As parcelas passadas e o registro da recorrência não são apagados.
     *
     * @param emailUsuario E-mail do usuário autenticado
     * @param id Id da recorrência
     */
    public void cancelarFuturas(String emailUsuario, Long id) {
        Recorrencia recorrencia = buscarRecorrenciaDoUsuario(emailUsuario, id);
        repositorioTransacao.deleteByRecorrenciaIdAndDataTransacaoGreaterThanEqual(recorrencia.getId(), LocalDate.now());
    }

    /**
     * Atualiza o valor das parcelas futuras de uma recorrência (a partir de hoje) e o valor
     * de referência da recorrência. As parcelas passadas não mudam.
     *
     * @param emailUsuario E-mail do usuário autenticado
     * @param id Id da recorrência
     * @param novoValor Novo valor a aplicar nas parcelas futuras
     * @return Recorrência atualizada
     */
    public RespostaRecorrencia atualizarValorFuturo(String emailUsuario, Long id, BigDecimal novoValor) {
        Recorrencia recorrencia = buscarRecorrenciaDoUsuario(emailUsuario, id);

        List<Transacao> futuras = repositorioTransacao
                .findByRecorrenciaIdAndDataTransacaoGreaterThanEqual(recorrencia.getId(), LocalDate.now());

        if (!futuras.isEmpty()) {
            futuras.forEach(transacao -> transacao.setValor(novoValor));
            repositorioTransacao.saveAll(futuras);

            recorrencia.setValor(novoValor);
            repositorioRecorrencia.save(recorrencia);
        }

        return paraResposta(recorrencia);
    }

    private Conta resolverConta(Long contaId, Long usuarioId) {
        return repositorioConta.findByIdAndUsuarioId(contaId, usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Conta não encontrada"));
    }

    private Cartao resolverCartao(Long cartaoId, Long usuarioId) {
        if (cartaoId == null) {
            return null;
        }

        return repositorioCartao.findByIdAndUsuarioId(cartaoId, usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cartão não encontrado"));
    }

    private Recorrencia buscarRecorrenciaDoUsuario(String emailUsuario, Long id) {
        Usuario usuario = buscarUsuario(emailUsuario);

        return repositorioRecorrencia.findByIdAndUsuarioId(id, usuario.getId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Recorrência não encontrada"));
    }

    private Usuario buscarUsuario(String email) {
        return repositorioUsuario.findByEmail(email)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado"));
    }

    private RespostaRecorrencia paraResposta(Recorrencia recorrencia) {
        Cartao cartao = recorrencia.getCartao();
        int parcelasRestantes = (int) repositorioTransacao
                .countByRecorrenciaIdAndDataTransacaoGreaterThanEqual(recorrencia.getId(), LocalDate.now());

        return new RespostaRecorrencia(
                recorrencia.getId(),
                recorrencia.getDescricao(),
                recorrencia.getValor(),
                recorrencia.getTipo(),
                recorrencia.getCategoria(),
                recorrencia.getConta().getId(),
                recorrencia.getConta().getNome(),
                cartao != null ? cartao.getId() : null,
                cartao != null ? cartao.getNome() : null,
                recorrencia.getTotalParcelas(),
                parcelasRestantes,
                recorrencia.getDataInicio()
        );
    }
}
