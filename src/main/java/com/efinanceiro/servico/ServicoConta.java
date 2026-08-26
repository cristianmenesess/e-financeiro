package com.efinanceiro.servico;

import com.efinanceiro.dominio.Conta;
import com.efinanceiro.dominio.Usuario;
import com.efinanceiro.dto.requisicao.RequisicaoConta;
import com.efinanceiro.dto.resposta.RespostaConta;
import com.efinanceiro.excecao.CredenciaisInvalidasException;
import com.efinanceiro.excecao.RecursoNaoEncontradoException;
import com.efinanceiro.repositorio.RepositorioConta;
import com.efinanceiro.repositorio.RepositorioRecorrencia;
import com.efinanceiro.repositorio.RepositorioTransacao;
import com.efinanceiro.repositorio.RepositorioUsuario;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ServicoConta {

    private final RepositorioConta repositorioConta;
    private final RepositorioUsuario repositorioUsuario;
    private final RepositorioTransacao repositorioTransacao;
    private final RepositorioRecorrencia repositorioRecorrencia;
    private final PasswordEncoder codificadorDeSenha;

    public ServicoConta(RepositorioConta repositorioConta,
                         RepositorioUsuario repositorioUsuario,
                         RepositorioTransacao repositorioTransacao,
                         RepositorioRecorrencia repositorioRecorrencia,
                         PasswordEncoder codificadorDeSenha) {
        this.repositorioConta = repositorioConta;
        this.repositorioUsuario = repositorioUsuario;
        this.repositorioTransacao = repositorioTransacao;
        this.repositorioRecorrencia = repositorioRecorrencia;
        this.codificadorDeSenha = codificadorDeSenha;
    }

    /**
     * Lista as contas do usuário autenticado.
     *
     * @param emailUsuario E-mail do usuário autenticado
     * @return Lista de contas do usuário
     */
    public List<RespostaConta> listarContas(String emailUsuario) {
        Usuario usuario = buscarUsuario(emailUsuario);

        return repositorioConta.findByUsuarioId(usuario.getId()).stream()
                .map(this::paraResposta)
                .toList();
    }

    /**
     * Cria uma nova conta para o usuário autenticado.
     *
     * @param emailUsuario E-mail do usuário autenticado
     * @param requisicao Dados da conta (nome e cores)
     * @return Conta criada
     */
    public RespostaConta criarConta(String emailUsuario, RequisicaoConta requisicao) {
        Usuario usuario = buscarUsuario(emailUsuario);

        Conta conta = new Conta();
        conta.setUsuario(usuario);
        conta.setNome(requisicao.nome());
        conta.setCorFundo(requisicao.corFundo());
        conta.setCorTexto(requisicao.corTexto());

        repositorioConta.save(conta);
        return paraResposta(conta);
    }

    /**
     * Atualiza nome e cores de uma conta do usuário autenticado.
     *
     * @param emailUsuario E-mail do usuário autenticado
     * @param id Id da conta a atualizar
     * @param requisicao Novos dados da conta
     * @return Conta atualizada
     */
    public RespostaConta atualizarConta(String emailUsuario, Long id, RequisicaoConta requisicao) {
        Conta conta = buscarContaDoUsuario(emailUsuario, id);

        conta.setNome(requisicao.nome());
        conta.setCorFundo(requisicao.corFundo());
        conta.setCorTexto(requisicao.corTexto());

        repositorioConta.save(conta);
        return paraResposta(conta);
    }

    /**
     * Exclui uma conta do usuário autenticado, junto com todas as transações e recorrências
     * vinculadas a ela, mediante confirmação de senha (exclusão em cascata, por isso a
     * confirmação extra).
     *
     * @param emailUsuario E-mail do usuário autenticado
     * @param id Id da conta a excluir
     * @param senha Senha atual do usuário, pra confirmar a exclusão
     */
    @Transactional
    public void excluirConta(String emailUsuario, Long id, String senha) {
        Usuario usuario = buscarUsuario(emailUsuario);
        Conta conta = buscarContaDoUsuario(usuario, id);

        if (!codificadorDeSenha.matches(senha, usuario.getSenhaHash())) {
            throw new CredenciaisInvalidasException("Senha incorreta");
        }

        repositorioTransacao.deleteByContaId(conta.getId());
        repositorioRecorrencia.deleteByContaId(conta.getId());
        repositorioConta.delete(conta);
    }

    private Conta buscarContaDoUsuario(String emailUsuario, Long id) {
        Usuario usuario = buscarUsuario(emailUsuario);

        return buscarContaDoUsuario(usuario, id);
    }

    private Conta buscarContaDoUsuario(Usuario usuario, Long id) {
        return repositorioConta.findByIdAndUsuarioId(id, usuario.getId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Conta não encontrada"));
    }

    private Usuario buscarUsuario(String email) {
        return repositorioUsuario.findByEmail(email)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado"));
    }

    private RespostaConta paraResposta(Conta conta) {
        return new RespostaConta(conta.getId(), conta.getNome(), conta.getCorFundo(), conta.getCorTexto());
    }
}
