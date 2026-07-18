package com.efinanceiro.servico;

import com.efinanceiro.dominio.Cartao;
import com.efinanceiro.dominio.TipoConta;
import com.efinanceiro.dominio.TipoTransacao;
import com.efinanceiro.dominio.Transacao;
import com.efinanceiro.dominio.Usuario;
import com.efinanceiro.dto.requisicao.RequisicaoTransacao;
import com.efinanceiro.dto.resposta.RespostaResumoSaldo;
import com.efinanceiro.dto.resposta.RespostaTransacao;
import com.efinanceiro.excecao.RecursoNaoEncontradoException;
import com.efinanceiro.repositorio.RepositorioCartao;
import com.efinanceiro.repositorio.RepositorioTransacao;
import com.efinanceiro.repositorio.RepositorioUsuario;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@Transactional
public class ServicoTransacao {

    private final RepositorioTransacao repositorioTransacao;
    private final RepositorioUsuario repositorioUsuario;
    private final RepositorioCartao repositorioCartao;

    public ServicoTransacao(RepositorioTransacao repositorioTransacao,
                             RepositorioUsuario repositorioUsuario,
                             RepositorioCartao repositorioCartao) {
        this.repositorioTransacao = repositorioTransacao;
        this.repositorioUsuario = repositorioUsuario;
        this.repositorioCartao = repositorioCartao;
    }

    /**
     * Lista as transações do usuário autenticado, filtradas por conta se informado.
     *
     * @param emailUsuario E-mail do usuário autenticado
     * @param conta Conta para filtrar (todas, cpf ou pj)
     * @return Lista de transações ordenadas da mais recente pra mais antiga
     */
    @Transactional(readOnly = true)
    public List<RespostaTransacao> listarTransacoes(String emailUsuario, String conta) {
        Usuario usuario = buscarUsuario(emailUsuario);
        List<Transacao> transacoes = buscarTransacoesFiltradas(usuario.getId(), conta);

        return transacoes.stream().map(this::paraResposta).toList();
    }

    /**
     * Calcula o resumo financeiro (saldo, entradas e saídas) do usuário autenticado, filtrado por conta se informado.
     *
     * @param emailUsuario E-mail do usuário autenticado
     * @param conta Conta para filtrar (todas, cpf ou pj)
     * @return Resumo com saldo, total de entradas e total de saídas
     */
    @Transactional(readOnly = true)
    public RespostaResumoSaldo buscarResumo(String emailUsuario, String conta) {
        Usuario usuario = buscarUsuario(emailUsuario);
        List<Transacao> transacoes = buscarTransacoesFiltradas(usuario.getId(), conta);

        BigDecimal totalEntradas = somarPorTipo(transacoes, TipoTransacao.ENTRADA);
        BigDecimal totalSaidas = somarPorTipo(transacoes, TipoTransacao.SAIDA);
        BigDecimal saldo = totalEntradas.subtract(totalSaidas);

        return new RespostaResumoSaldo(saldo, totalEntradas, totalSaidas);
    }

    /**
     * Cria uma nova transação para o usuário autenticado.
     *
     * @param emailUsuario E-mail do usuário autenticado
     * @param requisicao Dados da transação
     * @return Transação criada
     */
    public RespostaTransacao criarTransacao(String emailUsuario, RequisicaoTransacao requisicao) {
        Usuario usuario = buscarUsuario(emailUsuario);

        Transacao transacao = new Transacao();
        transacao.setUsuario(usuario);
        preencherTransacao(transacao, requisicao, usuario);

        repositorioTransacao.save(transacao);
        return paraResposta(transacao);
    }

    /**
     * Atualiza uma transação existente do usuário autenticado.
     *
     * @param emailUsuario E-mail do usuário autenticado
     * @param id Id da transação a atualizar
     * @param requisicao Novos dados da transação
     * @return Transação atualizada
     */
    public RespostaTransacao atualizarTransacao(String emailUsuario, Long id, RequisicaoTransacao requisicao) {
        Usuario usuario = buscarUsuario(emailUsuario);
        Transacao transacao = buscarTransacaoDoUsuario(usuario.getId(), id);

        preencherTransacao(transacao, requisicao, usuario);

        repositorioTransacao.save(transacao);
        return paraResposta(transacao);
    }

    /**
     * Exclui uma transação do usuário autenticado.
     *
     * @param emailUsuario E-mail do usuário autenticado
     * @param id Id da transação a excluir
     */
    public void excluirTransacao(String emailUsuario, Long id) {
        Usuario usuario = buscarUsuario(emailUsuario);
        Transacao transacao = buscarTransacaoDoUsuario(usuario.getId(), id);
        repositorioTransacao.delete(transacao);
    }

    private void preencherTransacao(Transacao transacao, RequisicaoTransacao requisicao, Usuario usuario) {
        transacao.setDescricao(requisicao.descricao());
        transacao.setValor(requisicao.valor());
        transacao.setTipo(requisicao.tipo());
        transacao.setConta(requisicao.conta());
        transacao.setCategoria(requisicao.categoria());
        transacao.setDataTransacao(requisicao.dataTransacao() != null ? requisicao.dataTransacao() : LocalDate.now());
        transacao.setCartao(resolverCartao(requisicao.cartaoId(), usuario.getId()));
    }

    private Cartao resolverCartao(Long cartaoId, Long usuarioId) {
        if (cartaoId == null) {
            return null;
        }

        return repositorioCartao.findByIdAndUsuarioId(cartaoId, usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cartão não encontrado"));
    }

    private List<Transacao> buscarTransacoesFiltradas(Long usuarioId, String conta) {
        if (conta == null || conta.equalsIgnoreCase("todas")) {
            return repositorioTransacao.findByUsuarioIdOrderByDataTransacaoDesc(usuarioId);
        }

        TipoConta tipoConta = TipoConta.valueOf(conta.toUpperCase());
        return repositorioTransacao.findByUsuarioIdAndContaOrderByDataTransacaoDesc(usuarioId, tipoConta);
    }

    private BigDecimal somarPorTipo(List<Transacao> transacoes, TipoTransacao tipo) {
        return transacoes.stream()
                .filter(transacao -> transacao.getTipo() == tipo)
                .map(Transacao::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Transacao buscarTransacaoDoUsuario(Long usuarioId, Long id) {
        return repositorioTransacao.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Transação não encontrada"));
    }

    private Usuario buscarUsuario(String email) {
        return repositorioUsuario.findByEmail(email)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado"));
    }

    private RespostaTransacao paraResposta(Transacao transacao) {
        Cartao cartao = transacao.getCartao();

        return new RespostaTransacao(
                transacao.getId(),
                transacao.getDescricao(),
                transacao.getValor(),
                transacao.getTipo(),
                transacao.getConta(),
                transacao.getCategoria(),
                cartao != null ? cartao.getId() : null,
                cartao != null ? cartao.getNome() : null,
                transacao.getDataTransacao()
        );
    }
}
