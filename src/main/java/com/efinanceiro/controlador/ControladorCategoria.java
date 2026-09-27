package com.efinanceiro.controlador;

import com.efinanceiro.dto.requisicao.RequisicaoAtualizacaoCategoria;
import com.efinanceiro.dto.requisicao.RequisicaoCategoria;
import com.efinanceiro.dto.resposta.RespostaCategoria;
import com.efinanceiro.dto.resposta.RespostaExclusaoCategoria;
import com.efinanceiro.dto.resposta.RespostaOpcoesCategoria;
import com.efinanceiro.servico.ServicoCategoria;
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
@RequestMapping("/api/categorias")
public class ControladorCategoria {

    private final ServicoCategoria servicoCategoria;

    public ControladorCategoria(ServicoCategoria servicoCategoria) {
        this.servicoCategoria = servicoCategoria;
    }

    /**
     * Lista as categorias fixas do sistema e as personalizadas do usuário autenticado.
     *
     * @param autenticacao Autenticação do usuário atual
     * @return Lista de categorias
     */
    @GetMapping
    public ResponseEntity<List<RespostaCategoria>> listarCategorias(Authentication autenticacao) {
        return ResponseEntity.ok(servicoCategoria.listarCategorias(autenticacao.getName()));
    }

    /**
     * Lista os ícones e tons que uma categoria pode usar.
     *
     * @return Ícones e tons permitidos
     */
    @GetMapping("/opcoes")
    public ResponseEntity<RespostaOpcoesCategoria> listarOpcoes() {
        return ResponseEntity.ok(servicoCategoria.listarOpcoes());
    }

    /**
     * Cria uma categoria personalizada.
     *
     * @param autenticacao Autenticação do usuário atual
     * @param requisicao Nome, tipo, ícone e tom
     * @return Categoria criada
     */
    @PostMapping
    public ResponseEntity<RespostaCategoria> criarCategoria(Authentication autenticacao,
                                                            @Valid @RequestBody RequisicaoCategoria requisicao) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicoCategoria.criarCategoria(autenticacao.getName(), requisicao));
    }

    /**
     * Atualiza nome, ícone e tom de uma categoria personalizada.
     *
     * @param autenticacao Autenticação do usuário atual
     * @param id Id da categoria
     * @param requisicao Novo nome, ícone e tom
     * @return Categoria atualizada
     */
    @PutMapping("/{id}")
    public ResponseEntity<RespostaCategoria> atualizarCategoria(Authentication autenticacao,
                                                                @PathVariable Long id,
                                                                @Valid @RequestBody RequisicaoAtualizacaoCategoria requisicao) {
        return ResponseEntity.ok(servicoCategoria.atualizarCategoria(autenticacao.getName(), id, requisicao));
    }

    /**
     * Exclui uma categoria personalizada, movendo as movimentações dela pra categoria fixa genérica.
     *
     * @param autenticacao Autenticação do usuário atual
     * @param id Id da categoria
     * @return Quantidade de movimentações e recorrências movidas
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<RespostaExclusaoCategoria> excluirCategoria(Authentication autenticacao, @PathVariable Long id) {
        return ResponseEntity.ok(servicoCategoria.excluirCategoria(autenticacao.getName(), id));
    }
}
