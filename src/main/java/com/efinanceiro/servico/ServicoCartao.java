package com.efinanceiro.servico;

import com.efinanceiro.dominio.Cartao;
import com.efinanceiro.dominio.TipoTransacao;
import com.efinanceiro.dominio.Transacao;
import com.efinanceiro.dominio.Usuario;
import com.efinanceiro.dto.requisicao.RequisicaoCartao;
import com.efinanceiro.dto.resposta.RespostaCartao;
import com.efinanceiro.excecao.RecursoNaoEncontradoException;
import com.efinanceiro.repositorio.RepositorioCartao;
import com.efinanceiro.repositorio.RepositorioTransacao;
import com.efinanceiro.repositorio.RepositorioUsuario;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class ServicoCartao {

    private final RepositorioCartao repositorioCartao;
    private final RepositorioUsuario repositorioUsuario;
    private final RepositorioTransacao repositorioTransacao;

    public ServicoCartao(RepositorioCartao repositorioCartao,
                          RepositorioUsuario repositorioUsuario,
                          RepositorioTransacao repositorioTransacao) {
        this.repositorioCartao = repositorioCartao;
        this.repositorioUsuario = repositorioUsuario;
        this.repositorioTransacao = repositorioTransacao;
    }

    /**
     * Lista os cartões do usuário autenticado, cada um com o gasto do mês atual calculado.
     *
     * @param emailUsuario E-mail do usuário autenticado
     * @return Lista de cartões com o respectivo gasto do mês
     */
    public List<RespostaCartao> listarCartoes(String emailUsuario) {
        Usuario usuario = buscarUsuario(emailUsuario);

        return repositorioCartao.findByUsuarioId(usuario.getId()).stream()
                .map(this::paraResposta)
                .toList();
    }

    /**
     * Cria um novo cartão para o usuário autenticado.
     *
     * @param emailUsuario E-mail do usuário autenticado
     * @param requisicao Dados do cartão (nome e cores)
     * @return Cartão criado
     */
    public RespostaCartao criarCartao(String emailUsuario, RequisicaoCartao requisicao) {
        Usuario usuario = buscarUsuario(emailUsuario);

        Cartao cartao = new Cartao();
        cartao.setUsuario(usuario);
        cartao.setNome(requisicao.nome());
        cartao.setCorFundo(requisicao.corFundo());
        cartao.setCorTexto(requisicao.corTexto());

        repositorioCartao.save(cartao);
        return paraResposta(cartao);
    }

    /**
     * Atualiza nome e cores de um cartão do usuário autenticado.
     *
     * @param emailUsuario E-mail do usuário autenticado
     * @param id Id do cartão a atualizar
     * @param requisicao Novos dados do cartão
     * @return Cartão atualizado
     */
    public RespostaCartao atualizarCartao(String emailUsuario, Long id, RequisicaoCartao requisicao) {
        Cartao cartao = buscarCartaoDoUsuario(emailUsuario, id);

        cartao.setNome(requisicao.nome());
        cartao.setCorFundo(requisicao.corFundo());
        cartao.setCorTexto(requisicao.corTexto());

        repositorioCartao.save(cartao);
        return paraResposta(cartao);
    }

    /**
     * Exclui um cartão do usuário autenticado.
     *
     * @param emailUsuario E-mail do usuário autenticado
     * @param id Id do cartão a excluir
     */
    public void excluirCartao(String emailUsuario, Long id) {
        Cartao cartao = buscarCartaoDoUsuario(emailUsuario, id);
        repositorioCartao.delete(cartao);
    }

    private Cartao buscarCartaoDoUsuario(String emailUsuario, Long id) {
        Usuario usuario = buscarUsuario(emailUsuario);

        return repositorioCartao.findByIdAndUsuarioId(id, usuario.getId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cartão não encontrado"));
    }

    private Usuario buscarUsuario(String email) {
        return repositorioUsuario.findByEmail(email)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado"));
    }

    private RespostaCartao paraResposta(Cartao cartao) {
        BigDecimal gastoNoMes = calcularGastoNoMes(cartao.getId());
        return new RespostaCartao(cartao.getId(), cartao.getNome(), cartao.getCorFundo(), cartao.getCorTexto(), gastoNoMes);
    }

    private BigDecimal calcularGastoNoMes(Long cartaoId) {
        LocalDate inicioMes = LocalDate.now().withDayOfMonth(1);
        LocalDate fimMes = inicioMes.withDayOfMonth(inicioMes.lengthOfMonth());

        return repositorioTransacao
                .findByCartaoIdAndTipoAndDataTransacaoBetween(cartaoId, TipoTransacao.SAIDA, inicioMes, fimMes)
                .stream()
                .map(Transacao::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
