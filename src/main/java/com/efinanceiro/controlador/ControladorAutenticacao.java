package com.efinanceiro.controlador;

import com.efinanceiro.dto.requisicao.RequisicaoCadastro;
import com.efinanceiro.dto.requisicao.RequisicaoEsqueciSenha;
import com.efinanceiro.dto.requisicao.RequisicaoLogin;
import com.efinanceiro.dto.requisicao.RequisicaoRedefinirSenha;
import com.efinanceiro.dto.resposta.RespostaAutenticacao;
import com.efinanceiro.servico.ServicoAutenticacao;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/autenticacao")
public class ControladorAutenticacao {

    private final ServicoAutenticacao servicoAutenticacao;

    public ControladorAutenticacao(ServicoAutenticacao servicoAutenticacao) {
        this.servicoAutenticacao = servicoAutenticacao;
    }

    /**
     * Cadastra um novo usuário no sistema.
     *
     * @param requisicao Dados de cadastro (nome, e-mail e senha)
     * @return Token de acesso e dados básicos do usuário criado
     */
    @PostMapping("/cadastro")
    public ResponseEntity<RespostaAutenticacao> cadastrar(@Valid @RequestBody RequisicaoCadastro requisicao) {
        RespostaAutenticacao resposta = servicoAutenticacao.cadastrar(requisicao);
        return ResponseEntity.status(HttpStatus.CREATED).body(resposta);
    }

    /**
     * Autentica um usuário e retorna um token de acesso.
     *
     * @param requisicao Credenciais de login (e-mail e senha)
     * @return Token de acesso e dados básicos do usuário autenticado
     */
    @PostMapping("/login")
    public ResponseEntity<RespostaAutenticacao> login(@Valid @RequestBody RequisicaoLogin requisicao) {
        RespostaAutenticacao resposta = servicoAutenticacao.login(requisicao);
        return ResponseEntity.ok(resposta);
    }

    /**
     * Solicita a redefinição de senha: se o e-mail informado estiver cadastrado, envia um link
     * de redefinição por e-mail. Sempre responde 200, mesmo se o e-mail não existir.
     *
     * @param requisicao E-mail do usuário que esqueceu a senha
     */
    @PostMapping("/esqueci-senha")
    public ResponseEntity<Void> esqueciSenha(@Valid @RequestBody RequisicaoEsqueciSenha requisicao) {
        servicoAutenticacao.esqueciSenha(requisicao);
        return ResponseEntity.ok().build();
    }

    /**
     * Redefine a senha do usuário a partir do token recebido por e-mail.
     *
     * @param requisicao Token de redefinição e a nova senha
     */
    @PostMapping("/redefinir-senha")
    public ResponseEntity<Void> redefinirSenha(@Valid @RequestBody RequisicaoRedefinirSenha requisicao) {
        servicoAutenticacao.redefinirSenha(requisicao);
        return ResponseEntity.ok().build();
    }
}
