package com.efinanceiro.servico;

import com.efinanceiro.dominio.Usuario;
import com.efinanceiro.dto.requisicao.RequisicaoCadastro;
import com.efinanceiro.dto.requisicao.RequisicaoLogin;
import com.efinanceiro.dto.resposta.RespostaAutenticacao;
import com.efinanceiro.excecao.CredenciaisInvalidasException;
import com.efinanceiro.excecao.EmailJaCadastradoException;
import com.efinanceiro.repositorio.RepositorioUsuario;
import com.efinanceiro.seguranca.ServicoJwt;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class ServicoAutenticacao {

    private final RepositorioUsuario repositorioUsuario;
    private final PasswordEncoder codificadorDeSenha;
    private final ServicoJwt servicoJwt;

    public ServicoAutenticacao(RepositorioUsuario repositorioUsuario,
                                PasswordEncoder codificadorDeSenha,
                                ServicoJwt servicoJwt) {
        this.repositorioUsuario = repositorioUsuario;
        this.codificadorDeSenha = codificadorDeSenha;
        this.servicoJwt = servicoJwt;
    }

    /**
     * Cadastra um novo usuário, criptografando a senha antes de salvar, e já retorna um token de acesso.
     *
     * @param requisicao Dados de cadastro (nome, e-mail e senha em texto puro)
     * @return Token JWT e dados básicos do usuário recém-criado
     */
    public RespostaAutenticacao cadastrar(RequisicaoCadastro requisicao) {
        if (repositorioUsuario.existsByEmail(requisicao.email())) {
            throw new EmailJaCadastradoException("Já existe um usuário cadastrado com esse e-mail");
        }

        Usuario usuario = new Usuario();
        usuario.setNome(requisicao.nome());
        usuario.setEmail(requisicao.email());
        usuario.setSenhaHash(codificadorDeSenha.encode(requisicao.senha()));

        repositorioUsuario.save(usuario);

        String token = servicoJwt.gerarToken(usuario.getEmail());
        return new RespostaAutenticacao(token, usuario.getNome(), usuario.getEmail());
    }

    /**
     * Autentica um usuário existente e gera um novo token de acesso.
     *
     * @param requisicao Credenciais de login (e-mail e senha)
     * @return Token JWT e dados básicos do usuário autenticado
     */
    public RespostaAutenticacao login(RequisicaoLogin requisicao) {
        Usuario usuario = repositorioUsuario.findByEmail(requisicao.email())
                .orElseThrow(() -> new CredenciaisInvalidasException("E-mail ou senha inválidos"));

        if (!codificadorDeSenha.matches(requisicao.senha(), usuario.getSenhaHash())) {
            throw new CredenciaisInvalidasException("E-mail ou senha inválidos");
        }

        String token = servicoJwt.gerarToken(usuario.getEmail());
        return new RespostaAutenticacao(token, usuario.getNome(), usuario.getEmail());
    }
}
