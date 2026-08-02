package com.efinanceiro.servico;

import com.efinanceiro.dominio.Conta;
import com.efinanceiro.dominio.Usuario;
import com.efinanceiro.dto.requisicao.RequisicaoCadastro;
import com.efinanceiro.dto.requisicao.RequisicaoEsqueciSenha;
import com.efinanceiro.dto.requisicao.RequisicaoLogin;
import com.efinanceiro.dto.requisicao.RequisicaoRedefinirSenha;
import com.efinanceiro.dto.resposta.RespostaAutenticacao;
import com.efinanceiro.excecao.CredenciaisInvalidasException;
import com.efinanceiro.excecao.EmailJaCadastradoException;
import com.efinanceiro.excecao.TokenInvalidoOuExpiradoException;
import com.efinanceiro.repositorio.RepositorioConta;
import com.efinanceiro.repositorio.RepositorioUsuario;
import com.efinanceiro.seguranca.ServicoJwt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class ServicoAutenticacao {

    private final RepositorioUsuario repositorioUsuario;
    private final PasswordEncoder codificadorDeSenha;
    private final ServicoJwt servicoJwt;
    private final ServicoEmail servicoEmail;
    private final RepositorioConta repositorioConta;
    private final String frontendUrl;

    public ServicoAutenticacao(RepositorioUsuario repositorioUsuario,
                                PasswordEncoder codificadorDeSenha,
                                ServicoJwt servicoJwt,
                                ServicoEmail servicoEmail,
                                RepositorioConta repositorioConta,
                                @Value("${app.frontend.url}") String frontendUrl) {
        this.repositorioUsuario = repositorioUsuario;
        this.codificadorDeSenha = codificadorDeSenha;
        this.servicoJwt = servicoJwt;
        this.servicoEmail = servicoEmail;
        this.repositorioConta = repositorioConta;
        this.frontendUrl = frontendUrl;
    }

    /**
     * Cadastra um novo usuário, criptografando a senha antes de salvar, e já retorna um token de acesso.
     *
     * @param requisicao Dados de cadastro (nome, e-mail e senha em texto puro)
     * @return Token JWT e dados básicos do usuário recém-criado
     */
    @Transactional
    public RespostaAutenticacao cadastrar(RequisicaoCadastro requisicao) {
        if (repositorioUsuario.existsByEmail(requisicao.email())) {
            throw new EmailJaCadastradoException("Já existe um usuário cadastrado com esse e-mail");
        }

        Usuario usuario = new Usuario();
        usuario.setNome(requisicao.nome());
        usuario.setEmail(requisicao.email());
        usuario.setSenhaHash(codificadorDeSenha.encode(requisicao.senha()));

        repositorioUsuario.save(usuario);
        criarContaPadrao(usuario);

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

    /**
     * Solicita a redefinição de senha: se o e-mail estiver cadastrado, gera um token de reset,
     * salva apenas o hash dele (com expiração de 1 hora) e envia o link por e-mail. Não revela
     * se o e-mail existe ou não — sempre retorna normalmente.
     *
     * @param requisicao E-mail do usuário que esqueceu a senha
     */
    public void esqueciSenha(RequisicaoEsqueciSenha requisicao) {
        repositorioUsuario.findByEmail(requisicao.email()).ifPresent(usuario -> {
            String token = UUID.randomUUID().toString().replace("-", "");

            usuario.setTokenRedefinicaoHash(sha256(token));
            usuario.setTokenRedefinicaoExpiraEm(Instant.now().plus(1, ChronoUnit.HOURS));
            repositorioUsuario.save(usuario);

            String link = frontendUrl + "/redefinir-senha.html?token=" + token;
            servicoEmail.enviarEmailRedefinicaoSenha(usuario.getEmail(), usuario.getNome(), link);
        });
    }

    /**
     * Redefine a senha do usuário a partir de um token de reset válido e ainda não expirado.
     * O token é invalidado após o uso (uso único).
     *
     * @param requisicao Token recebido por e-mail e a nova senha
     */
    public void redefinirSenha(RequisicaoRedefinirSenha requisicao) {
        String hash = sha256(requisicao.token());

        Usuario usuario = repositorioUsuario.findByTokenRedefinicaoHash(hash)
                .filter(u -> u.getTokenRedefinicaoExpiraEm() != null
                        && u.getTokenRedefinicaoExpiraEm().isAfter(Instant.now()))
                .orElseThrow(() -> new TokenInvalidoOuExpiradoException("Link de redefinição inválido ou expirado"));

        usuario.setSenhaHash(codificadorDeSenha.encode(requisicao.novaSenha()));
        usuario.setTokenRedefinicaoHash(null);
        usuario.setTokenRedefinicaoExpiraEm(null);
        repositorioUsuario.save(usuario);
    }

    private void criarContaPadrao(Usuario usuario) {
        Conta conta = new Conta();
        conta.setUsuario(usuario);
        conta.setNome("Pessoal");
        conta.setCorFundo("#E1F5EE");
        conta.setCorTexto("#0F6E56");

        repositorioConta.save(conta);
    }

    private String sha256(String valor) {
        try {
            MessageDigest digestor = MessageDigest.getInstance("SHA-256");
            byte[] hash = digestor.digest(valor.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo SHA-256 indisponível", e);
        }
    }
}
