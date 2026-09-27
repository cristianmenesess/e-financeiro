package com.efinanceiro.servico;

import com.efinanceiro.dominio.Cartao;
import com.efinanceiro.dominio.Categoria;
import com.efinanceiro.dominio.Conta;
import com.efinanceiro.dominio.Usuario;
import com.efinanceiro.excecao.RecursoNaoEncontradoException;
import com.efinanceiro.repositorio.RepositorioCartao;
import com.efinanceiro.repositorio.RepositorioCategoria;
import com.efinanceiro.repositorio.RepositorioConta;
import com.efinanceiro.repositorio.RepositorioUsuario;
import com.efinanceiro.seguranca.UsuarioAutenticado;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Buscas que todos os serviços repetiam: o usuário logado e os recursos dele (conta, cartão),
 * sempre garantindo que o recurso pertence ao usuário.
 */
@Component
public class BuscadorRecursosDoUsuario {

    private final RepositorioUsuario repositorioUsuario;
    private final RepositorioConta repositorioConta;
    private final RepositorioCartao repositorioCartao;
    private final RepositorioCategoria repositorioCategoria;

    public BuscadorRecursosDoUsuario(RepositorioUsuario repositorioUsuario,
                                     RepositorioConta repositorioConta,
                                     RepositorioCartao repositorioCartao,
                                     RepositorioCategoria repositorioCategoria) {
        this.repositorioUsuario = repositorioUsuario;
        this.repositorioConta = repositorioConta;
        this.repositorioCartao = repositorioCartao;
        this.repositorioCategoria = repositorioCategoria;
    }

    /**
     * Busca o usuário pelo e-mail. Quando é o próprio usuário logado (caso normal, vindo do
     * controller), o filtro JWT já carregou ele do banco nessa requisição: devolve só uma referência
     * pelo id, sem consultar de novo. Os campos do usuário só são lidos do banco se alguém acessar
     * (ex: a senha, na exclusão de conta) — por isso quem chama precisa estar numa transação.
     *
     * @param email E-mail do usuário (normalmente autenticacao.getName())
     * @return Usuário encontrado
     */
    public Usuario buscarUsuario(String email) {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();

        if (autenticacao != null
                && autenticacao.getPrincipal() instanceof UsuarioAutenticado usuarioLogado
                && usuarioLogado.getUsername().equals(email)) {
            return repositorioUsuario.getReferenceById(usuarioLogado.getId());
        }

        return repositorioUsuario.findByEmail(email)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado"));
    }

    /**
     * Busca uma conta pelo id, garantindo que pertence ao usuário.
     *
     * @param contaId Id da conta
     * @param usuarioId Id do usuário dono da conta
     * @return Conta encontrada
     */
    public Conta buscarConta(Long contaId, Long usuarioId) {
        return repositorioConta.findByIdAndUsuarioId(contaId, usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Conta não encontrada"));
    }

    /**
     * Busca um cartão pelo id, garantindo que pertence ao usuário.
     *
     * @param cartaoId Id do cartão
     * @param usuarioId Id do usuário dono do cartão
     * @return Cartão encontrado
     */
    public Cartao buscarCartao(Long cartaoId, Long usuarioId) {
        return repositorioCartao.findByIdAndUsuarioId(cartaoId, usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cartão não encontrado"));
    }

    /**
     * Busca um cartão opcional (transação/recorrência sem cartão = débito ou dinheiro).
     *
     * @param cartaoId Id do cartão, ou null
     * @param usuarioId Id do usuário dono do cartão
     * @return Cartão encontrado, ou null se nenhum cartão foi informado
     */
    public Cartao buscarCartaoOpcional(Long cartaoId, Long usuarioId) {
        return cartaoId == null ? null : buscarCartao(cartaoId, usuarioId);
    }

    /**
     * Busca uma categoria que o usuário pode usar: uma fixa do sistema ou uma personalizada dele.
     *
     * @param categoriaId Id da categoria
     * @param usuarioId Id do usuário
     * @return Categoria encontrada
     */
    public Categoria buscarCategoria(Long categoriaId, Long usuarioId) {
        return repositorioCategoria.buscarVisivelAoUsuario(categoriaId, usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Categoria não encontrada"));
    }
}
