package com.efinanceiro.controlador;

import com.efinanceiro.dto.requisicao.RequisicaoAssinatura;
import com.efinanceiro.dto.resposta.RespostaAssinatura;
import com.efinanceiro.servico.ServicoAssinatura;
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
@RequestMapping("/api/assinaturas")
public class ControladorAssinatura {

    private final ServicoAssinatura servicoAssinatura;

    public ControladorAssinatura(ServicoAssinatura servicoAssinatura) {
        this.servicoAssinatura = servicoAssinatura;
    }

    /**
     * Lista as assinaturas do usuário autenticado.
     *
     * @param autenticacao Autenticação do usuário atual
     * @return Assinaturas, ativas primeiro
     */
    @GetMapping
    public ResponseEntity<List<RespostaAssinatura>> listarAssinaturas(Authentication autenticacao) {
        return ResponseEntity.ok(servicoAssinatura.listarAssinaturas(autenticacao.getName()));
    }

    /**
     * Cria uma assinatura e lança as cobranças.
     *
     * @param autenticacao Autenticação do usuário atual
     * @param requisicao Dados da assinatura
     * @return Assinatura criada
     */
    @PostMapping
    public ResponseEntity<RespostaAssinatura> criarAssinatura(Authentication autenticacao,
                                                              @Valid @RequestBody RequisicaoAssinatura requisicao) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicoAssinatura.criarAssinatura(autenticacao.getName(), requisicao));
    }

    /**
     * Edita uma assinatura ativa, refazendo só as próximas cobranças ou todas.
     *
     * @param autenticacao Autenticação do usuário atual
     * @param id Id da assinatura
     * @param requisicao Novos dados e o alcance da edição
     * @return Assinatura atualizada
     */
    @PutMapping("/{id}")
    public ResponseEntity<RespostaAssinatura> atualizarAssinatura(Authentication autenticacao,
                                                                  @PathVariable Long id,
                                                                  @Valid @RequestBody RequisicaoAssinatura requisicao) {
        return ResponseEntity.ok(servicoAssinatura.atualizarAssinatura(autenticacao.getName(), id, requisicao));
    }

    /**
     * Cancela uma assinatura: as cobranças depois de hoje são apagadas e as passadas ficam.
     *
     * @param autenticacao Autenticação do usuário atual
     * @param id Id da assinatura
     * @return Assinatura cancelada
     */
    @PostMapping("/{id}/cancelar")
    public ResponseEntity<RespostaAssinatura> cancelarAssinatura(Authentication autenticacao, @PathVariable Long id) {
        return ResponseEntity.ok(servicoAssinatura.cancelarAssinatura(autenticacao.getName(), id));
    }

    /**
     * Exclui uma assinatura com todas as cobranças, inclusive as passadas.
     *
     * @param autenticacao Autenticação do usuário atual
     * @param id Id da assinatura
     * @return Resposta vazia com status 204
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluirAssinatura(Authentication autenticacao, @PathVariable Long id) {
        servicoAssinatura.excluirAssinatura(autenticacao.getName(), id);
        return ResponseEntity.noContent().build();
    }
}
