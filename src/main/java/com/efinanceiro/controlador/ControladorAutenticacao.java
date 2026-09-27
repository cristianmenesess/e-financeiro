package com.efinanceiro.controlador;

import com.efinanceiro.dto.requisicao.RequisicaoCadastro;
import com.efinanceiro.dto.requisicao.RequisicaoEsqueciSenha;
import com.efinanceiro.dto.requisicao.RequisicaoLogin;
import com.efinanceiro.dto.requisicao.RequisicaoRedefinirSenha;
import com.efinanceiro.dto.resposta.RespostaAutenticacao;
import com.efinanceiro.servico.ServicoAutenticacao;
import jakarta.servlet.http.HttpServletRequest;
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
     * @param requisicaoHttp Requisição HTTP, de onde vem o IP do cliente (limite de tentativas)
     * @return Token de acesso e dados básicos do usuário criado
     */
    @PostMapping("/cadastro")
    public ResponseEntity<RespostaAutenticacao> cadastrar(@Valid @RequestBody RequisicaoCadastro requisicao,
                                                          HttpServletRequest requisicaoHttp) {
        RespostaAutenticacao resposta = servicoAutenticacao.cadastrar(requisicao, requisicaoHttp.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).body(resposta);
    }

    /**
     * Autentica um usuário e retorna um token de acesso.
     *
     * @param requisicao Credenciais de login (e-mail e senha)
     * @param requisicaoHttp Requisição HTTP, de onde vem o IP do cliente (limite de tentativas)
     * @return Token de acesso e dados básicos do usuário autenticado
     */
    @PostMapping("/login")
    public ResponseEntity<RespostaAutenticacao> login(@Valid @RequestBody RequisicaoLogin requisicao,
                                                      HttpServletRequest requisicaoHttp) {
        RespostaAutenticacao resposta = servicoAutenticacao.login(requisicao, requisicaoHttp.getRemoteAddr());
        return ResponseEntity.ok(resposta);
    }

    /**
     * Solicita a redefinição de senha: se o e-mail informado estiver cadastrado, envia um link
     * de redefinição por e-mail. Sempre responde 200, mesmo se o e-mail não existir.
     *
     * @param requisicao E-mail do usuário que esqueceu a senha
     * @param requisicaoHttp Requisição HTTP, de onde vem o IP do cliente (limite de tentativas)
     */
    @PostMapping("/esqueci-senha")
    public ResponseEntity<Void> esqueciSenha(@Valid @RequestBody RequisicaoEsqueciSenha requisicao,
                                             HttpServletRequest requisicaoHttp) {
        servicoAutenticacao.esqueciSenha(requisicao, requisicaoHttp.getRemoteAddr());
        return ResponseEntity.ok().build();
    }

    /**
     * Redefine a senha do usuário a partir do token recebido por e-mail.
     *
     * @param requisicao Token de redefinição e a nova senha
     * @param requisicaoHttp Requisição HTTP, de onde vem o IP do cliente (limite de tentativas)
     */
    @PostMapping("/redefinir-senha")
    public ResponseEntity<Void> redefinirSenha(@Valid @RequestBody RequisicaoRedefinirSenha requisicao,
                                               HttpServletRequest requisicaoHttp) {
        servicoAutenticacao.redefinirSenha(requisicao, requisicaoHttp.getRemoteAddr());
        return ResponseEntity.ok().build();
    }
}
