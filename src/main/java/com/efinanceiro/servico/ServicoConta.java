package com.efinanceiro.servico;

import com.efinanceiro.dominio.Conta;
import com.efinanceiro.dominio.Usuario;
import com.efinanceiro.dto.requisicao.RequisicaoConta;
import com.efinanceiro.dto.resposta.RespostaConta;
import com.efinanceiro.excecao.CredenciaisInvalidasException;
import com.efinanceiro.excecao.RegraDeNegocioException;
import com.efinanceiro.repositorio.RepositorioConta;
import com.efinanceiro.repositorio.RepositorioRecorrencia;
import com.efinanceiro.repositorio.RepositorioTransacao;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class ServicoConta {

    private final RepositorioConta repositorioConta;
    private final RepositorioTransacao repositorioTransacao;
    private final RepositorioRecorrencia repositorioRecorrencia;
    private final BuscadorRecursosDoUsuario buscadorRecursosDoUsuario;
    private final PasswordEncoder codificadorDeSenha;

    public ServicoConta(RepositorioConta repositorioConta,
                         RepositorioTransacao repositorioTransacao,
                         RepositorioRecorrencia repositorioRecorrencia,
                         BuscadorRecursosDoUsuario buscadorRecursosDoUsuario,
                         PasswordEncoder codificadorDeSenha) {
        this.repositorioConta = repositorioConta;
        this.repositorioTransacao = repositorioTransacao;
        this.repositorioRecorrencia = repositorioRecorrencia;
        this.buscadorRecursosDoUsuario = buscadorRecursosDoUsuario;
        this.codificadorDeSenha = codificadorDeSenha;
    }

    /**
     * Lista as contas do usuário autenticado.
     *
     * @param emailUsuario E-mail do usuário autenticado
     * @return Lista de contas do usuário
     */
    @Transactional(readOnly = true)
    public List<RespostaConta> listarContas(String emailUsuario) {
        Usuario usuario = buscadorRecursosDoUsuario.buscarUsuario(emailUsuario);

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
        Usuario usuario = buscadorRecursosDoUsuario.buscarUsuario(emailUsuario);

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
        Usuario usuario = buscadorRecursosDoUsuario.buscarUsuario(emailUsuario);
        Conta conta = buscadorRecursosDoUsuario.buscarConta(id, usuario.getId());

        conta.setNome(requisicao.nome());
        conta.setCorFundo(requisicao.corFundo());
        conta.setCorTexto(requisicao.corTexto());

        repositorioConta.save(conta);
        return paraResposta(conta);
    }

    /**
     * Exclui uma conta do usuário autenticado, junto com todas as transações e recorrências
     * vinculadas a ela, mediante confirmação de senha (exclusão em cascata, por isso a
     * confirmação extra). A última conta não pode ser excluída — sem conta, o usuário não
     * conseguiria lançar nenhuma transação.
     *
     * @param emailUsuario E-mail do usuário autenticado
     * @param id Id da conta a excluir
     * @param senha Senha atual do usuário, pra confirmar a exclusão
     */
    public void excluirConta(String emailUsuario, Long id, String senha) {
        Usuario usuario = buscadorRecursosDoUsuario.buscarUsuario(emailUsuario);
        Conta conta = buscadorRecursosDoUsuario.buscarConta(id, usuario.getId());

        if (!codificadorDeSenha.matches(senha, usuario.getSenhaHash())) {
            throw new CredenciaisInvalidasException("Senha incorreta");
        }

        if (repositorioConta.countByUsuarioId(usuario.getId()) <= 1) {
            throw new RegraDeNegocioException("Não é possível excluir a única conta. Crie outra conta antes de excluir esta.");
        }

        repositorioTransacao.deleteByContaId(conta.getId());
        repositorioRecorrencia.deleteByContaId(conta.getId());
        repositorioConta.delete(conta);
    }

    private RespostaConta paraResposta(Conta conta) {
        return new RespostaConta(conta.getId(), conta.getNome(), conta.getCorFundo(), conta.getCorTexto());
    }
}
