package com.efinanceiro.controlador;

import com.efinanceiro.dto.requisicao.RequisicaoCartao;
import com.efinanceiro.dto.resposta.RespostaCartao;
import com.efinanceiro.servico.ServicoCartao;
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
@RequestMapping("/api/cartoes")
public class ControladorCartao {

    private final ServicoCartao servicoCartao;

    public ControladorCartao(ServicoCartao servicoCartao) {
        this.servicoCartao = servicoCartao;
    }

    /**
     * Lista os cartões do usuário autenticado.
     *
     * @param autenticacao Autenticação do usuário atual, injetada pelo Spring Security
     * @return Lista de cartões com o gasto do mês
     */
    @GetMapping
    public ResponseEntity<List<RespostaCartao>> listarCartoes(Authentication autenticacao) {
        return ResponseEntity.ok(servicoCartao.listarCartoes(autenticacao.getName()));
    }

    /**
     * Cria um novo cartão para o usuário autenticado.
     *
     * @param autenticacao Autenticação do usuário atual
     * @param requisicao Dados do cartão
     * @return Cartão criado
     */
    @PostMapping
    public ResponseEntity<RespostaCartao> criarCartao(Authentication autenticacao,
                                                        @Valid @RequestBody RequisicaoCartao requisicao) {
        RespostaCartao resposta = servicoCartao.criarCartao(autenticacao.getName(), requisicao);
        return ResponseEntity.status(HttpStatus.CREATED).body(resposta);
    }

    /**
     * Atualiza um cartão existente do usuário autenticado.
     *
     * @param autenticacao Autenticação do usuário atual
     * @param id Id do cartão
     * @param requisicao Novos dados do cartão
     * @return Cartão atualizado
     */
    @PutMapping("/{id}")
    public ResponseEntity<RespostaCartao> atualizarCartao(Authentication autenticacao,
                                                            @PathVariable Long id,
                                                            @Valid @RequestBody RequisicaoCartao requisicao) {
        return ResponseEntity.ok(servicoCartao.atualizarCartao(autenticacao.getName(), id, requisicao));
    }

    /**
     * Exclui um cartão do usuário autenticado.
     *
     * @param autenticacao Autenticação do usuário atual
     * @param id Id do cartão
     * @return Resposta vazia com status 204
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluirCartao(Authentication autenticacao, @PathVariable Long id) {
        servicoCartao.excluirCartao(autenticacao.getName(), id);
        return ResponseEntity.noContent().build();
    }
}
