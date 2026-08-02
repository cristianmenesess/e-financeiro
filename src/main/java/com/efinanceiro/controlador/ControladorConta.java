package com.efinanceiro.controlador;

import com.efinanceiro.dto.requisicao.RequisicaoConta;
import com.efinanceiro.dto.requisicao.RequisicaoExclusaoConta;
import com.efinanceiro.dto.resposta.RespostaConta;
import com.efinanceiro.servico.ServicoConta;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/contas")
public class ControladorConta {

    private final ServicoConta servicoConta;

    public ControladorConta(ServicoConta servicoConta) {
        this.servicoConta = servicoConta;
    }

    /**
     * Lista as contas do usuário autenticado.
     *
     * @param autenticacao Autenticação do usuário atual, injetada pelo Spring Security
     * @return Lista de contas
     */
    @GetMapping
    public ResponseEntity<List<RespostaConta>> listarContas(Authentication autenticacao) {
        return ResponseEntity.ok(servicoConta.listarContas(autenticacao.getName()));
    }

    /**
     * Cria uma nova conta para o usuário autenticado.
     *
     * @param autenticacao Autenticação do usuário atual
     * @param requisicao Dados da conta
     * @return Conta criada
     */
    @PostMapping
    public ResponseEntity<RespostaConta> criarConta(Authentication autenticacao,
                                                      @Valid @RequestBody RequisicaoConta requisicao) {
        RespostaConta resposta = servicoConta.criarConta(autenticacao.getName(), requisicao);
        return ResponseEntity.status(HttpStatus.CREATED).body(resposta);
    }

    /**
     * Atualiza uma conta existente do usuário autenticado.
     *
     * @param autenticacao Autenticação do usuário atual
     * @param id Id da conta
     * @param requisicao Novos dados da conta
     * @return Conta atualizada
     */
    @PutMapping("/{id}")
    public ResponseEntity<RespostaConta> atualizarConta(Authentication autenticacao,
                                                          @PathVariable Long id,
                                                          @Valid @RequestBody RequisicaoConta requisicao) {
        return ResponseEntity.ok(servicoConta.atualizarConta(autenticacao.getName(), id, requisicao));
    }

    /**
     * Exclui uma conta do usuário autenticado e todas as transações vinculadas a ela.
     * Exige a senha atual no corpo da requisição, por ser uma exclusão em cascata.
     *
     * @param autenticacao Autenticação do usuário atual
     * @param id Id da conta
     * @param requisicao Senha atual do usuário, para confirmação
     * @return Resposta vazia com status 204
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluirConta(Authentication autenticacao,
                                              @PathVariable Long id,
                                              @Valid @RequestBody RequisicaoExclusaoConta requisicao) {
        servicoConta.excluirConta(autenticacao.getName(), id, requisicao.senha());
        return ResponseEntity.noContent().build();
    }
}
