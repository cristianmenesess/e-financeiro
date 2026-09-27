package com.efinanceiro.controlador;

import com.efinanceiro.dto.requisicao.RequisicaoTransacao;
import com.efinanceiro.dto.resposta.RespostaResumoSaldo;
import com.efinanceiro.dto.resposta.RespostaTransacao;
import com.efinanceiro.servico.ServicoTransacao;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/transacoes")
public class ControladorTransacao {

    private final ServicoTransacao servicoTransacao;

    public ControladorTransacao(ServicoTransacao servicoTransacao) {
        this.servicoTransacao = servicoTransacao;
    }

    /**
     * Lista as transações do usuário autenticado, filtradas por conta se informado. Sem
     * {@code pagina}/{@code tamanho} devolve todas; com eles, só a página pedida — a resposta
     * continua sendo uma lista, e o total de transações vai no cabeçalho {@code X-Total-Count}.
     *
     * @param autenticacao Autenticação do usuário atual
     * @param contaId Id da conta para filtrar, ou null para todas as contas
     * @param pagina Número da página, começando em 0 (opcional)
     * @param tamanho Itens por página, de 1 a 200 (opcional, padrão 50 quando só a página é informada)
     * @return Lista de transações
     */
    @GetMapping
    public ResponseEntity<List<RespostaTransacao>> listarTransacoes(
            Authentication autenticacao,
            @RequestParam(required = false) Long contaId,
            @RequestParam(required = false) @Min(value = 0, message = "A página deve ser 0 ou maior") Integer pagina,
            @RequestParam(required = false)
            @Min(value = 1, message = "O tamanho da página deve ser no mínimo 1")
            @Max(value = 200, message = "O tamanho da página deve ser no máximo 200") Integer tamanho) {

        Page<RespostaTransacao> transacoes = servicoTransacao.listarTransacoes(autenticacao.getName(), contaId, pagina, tamanho);

        if (transacoes.getPageable().isUnpaged()) {
            return ResponseEntity.ok(transacoes.getContent());
        }

        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(transacoes.getTotalElements()))
                .body(transacoes.getContent());
    }

    /**
     * Retorna o resumo financeiro (saldo, entradas e saídas) do usuário autenticado.
     *
     * @param autenticacao Autenticação do usuário atual
     * @param contaId Id da conta para filtrar, ou null para todas as contas
     * @return Resumo financeiro
     */
    @GetMapping("/resumo")
    public ResponseEntity<RespostaResumoSaldo> buscarResumo(Authentication autenticacao,
                                                              @RequestParam(required = false) Long contaId) {
        return ResponseEntity.ok(servicoTransacao.buscarResumo(autenticacao.getName(), contaId));
    }

    /**
     * Cria uma nova transação para o usuário autenticado.
     *
     * @param autenticacao Autenticação do usuário atual
     * @param requisicao Dados da transação
     * @return Transação criada
     */
    @PostMapping
    public ResponseEntity<RespostaTransacao> criarTransacao(Authentication autenticacao,
                                                              @Valid @RequestBody RequisicaoTransacao requisicao) {
        RespostaTransacao resposta = servicoTransacao.criarTransacao(autenticacao.getName(), requisicao);
        return ResponseEntity.status(HttpStatus.CREATED).body(resposta);
    }

    /**
     * Atualiza uma transação existente do usuário autenticado.
     *
     * @param autenticacao Autenticação do usuário atual
     * @param id Id da transação
     * @param requisicao Novos dados da transação
     * @return Transação atualizada
     */
    @PutMapping("/{id}")
    public ResponseEntity<RespostaTransacao> atualizarTransacao(Authentication autenticacao,
                                                                  @PathVariable Long id,
                                                                  @Valid @RequestBody RequisicaoTransacao requisicao) {
        return ResponseEntity.ok(servicoTransacao.atualizarTransacao(autenticacao.getName(), id, requisicao));
    }

    /**
     * Exclui uma transação do usuário autenticado.
     *
     * @param autenticacao Autenticação do usuário atual
     * @param id Id da transação
     * @return Resposta vazia com status 204
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluirTransacao(Authentication autenticacao, @PathVariable Long id) {
        servicoTransacao.excluirTransacao(autenticacao.getName(), id);
        return ResponseEntity.noContent().build();
    }
}
