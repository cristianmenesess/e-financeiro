package com.efinanceiro.controlador;

import com.efinanceiro.dto.requisicao.RequisicaoAtualizacaoValorRecorrencia;
import com.efinanceiro.dto.requisicao.RequisicaoRecorrencia;
import com.efinanceiro.dto.resposta.RespostaRecorrencia;
import com.efinanceiro.servico.ServicoRecorrencia;
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
@RequestMapping("/api/recorrencias")
public class ControladorRecorrencia {

    private final ServicoRecorrencia servicoRecorrencia;

    public ControladorRecorrencia(ServicoRecorrencia servicoRecorrencia) {
        this.servicoRecorrencia = servicoRecorrencia;
    }

    /**
     * Lista as recorrências do usuário autenticado.
     *
     * @param autenticacao Autenticação do usuário atual, injetada pelo Spring Security
     * @return Lista de recorrências
     */
    @GetMapping
    public ResponseEntity<List<RespostaRecorrencia>> listarRecorrencias(Authentication autenticacao) {
        return ResponseEntity.ok(servicoRecorrencia.listarRecorrencias(autenticacao.getName()));
    }

    /**
     * Cria uma nova recorrência para o usuário autenticado e gera todas as parcelas.
     *
     * @param autenticacao Autenticação do usuário atual
     * @param requisicao Dados da recorrência
     * @return Recorrência criada
     */
    @PostMapping
    public ResponseEntity<RespostaRecorrencia> criarRecorrencia(Authentication autenticacao,
                                                                  @Valid @RequestBody RequisicaoRecorrencia requisicao) {
        RespostaRecorrencia resposta = servicoRecorrencia.criarRecorrencia(autenticacao.getName(), requisicao);
        return ResponseEntity.status(HttpStatus.CREATED).body(resposta);
    }

    /**
     * Cancela as parcelas futuras de uma recorrência do usuário autenticado.
     *
     * @param autenticacao Autenticação do usuário atual
     * @param id Id da recorrência
     * @return Resposta vazia com status 204
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelarFuturas(Authentication autenticacao, @PathVariable Long id) {
        servicoRecorrencia.cancelarFuturas(autenticacao.getName(), id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Atualiza o valor das parcelas futuras de uma recorrência do usuário autenticado.
     *
     * @param autenticacao Autenticação do usuário atual
     * @param id Id da recorrência
     * @param requisicao Novo valor a aplicar nas parcelas futuras
     * @return Recorrência atualizada
     */
    @PutMapping("/{id}/valor")
    public ResponseEntity<RespostaRecorrencia> atualizarValorFuturo(Authentication autenticacao,
                                                                      @PathVariable Long id,
                                                                      @Valid @RequestBody RequisicaoAtualizacaoValorRecorrencia requisicao) {
        return ResponseEntity.ok(servicoRecorrencia.atualizarValorFuturo(autenticacao.getName(), id, requisicao.valor()));
    }
}
