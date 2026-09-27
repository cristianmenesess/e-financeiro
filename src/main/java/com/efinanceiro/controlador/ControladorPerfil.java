package com.efinanceiro.controlador;

import com.efinanceiro.dto.requisicao.RequisicaoAtualizacaoPerfil;
import com.efinanceiro.dto.requisicao.RequisicaoExclusaoCadastro;
import com.efinanceiro.dto.requisicao.RequisicaoTrocaSenha;
import com.efinanceiro.dto.resposta.RespostaPerfil;
import com.efinanceiro.servico.ServicoPerfil;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/perfil")
public class ControladorPerfil {

    private final ServicoPerfil servicoPerfil;

    public ControladorPerfil(ServicoPerfil servicoPerfil) {
        this.servicoPerfil = servicoPerfil;
    }

    /**
     * Retorna os dados do perfil do usuário autenticado.
     *
     * @param autenticacao Autenticação do usuário atual
     * @return Nome, e-mail e foto
     */
    @GetMapping
    public ResponseEntity<RespostaPerfil> buscarPerfil(Authentication autenticacao) {
        return ResponseEntity.ok(servicoPerfil.buscarPerfil(autenticacao.getName()));
    }

    /**
     * Atualiza nome e e-mail do usuário autenticado (trocar o e-mail exige a senha atual e
     * devolve um token novo).
     *
     * @param autenticacao Autenticação do usuário atual
     * @param requisicao Novo nome, novo e-mail e, se o e-mail mudar, a senha atual
     * @return Perfil atualizado
     */
    @PutMapping
    public ResponseEntity<RespostaPerfil> atualizarPerfil(Authentication autenticacao,
                                                          @Valid @RequestBody RequisicaoAtualizacaoPerfil requisicao) {
        return ResponseEntity.ok(servicoPerfil.atualizarPerfil(autenticacao.getName(), requisicao));
    }

    /**
     * Troca a senha do usuário autenticado e devolve um token novo.
     *
     * @param autenticacao Autenticação do usuário atual
     * @param requisicao Senha atual e nova senha
     * @return Perfil com o token novo
     */
    @PutMapping("/senha")
    public ResponseEntity<RespostaPerfil> trocarSenha(Authentication autenticacao,
                                                      @Valid @RequestBody RequisicaoTrocaSenha requisicao) {
        return ResponseEntity.ok(servicoPerfil.trocarSenha(autenticacao.getName(), requisicao));
    }

    /**
     * Troca a foto de perfil do usuário autenticado.
     *
     * @param autenticacao Autenticação do usuário atual
     * @param foto Imagem JPG, PNG ou WEBP de até 5 MB, no campo multipart "foto"
     * @return Perfil com a URL da foto nova
     */
    @PutMapping("/foto")
    public ResponseEntity<RespostaPerfil> atualizarFoto(Authentication autenticacao,
                                                        @RequestPart("foto") MultipartFile foto) {
        return ResponseEntity.ok(servicoPerfil.atualizarFoto(autenticacao.getName(), foto));
    }

    /**
     * Remove a foto de perfil do usuário autenticado.
     *
     * @param autenticacao Autenticação do usuário atual
     * @return Perfil sem foto
     */
    @DeleteMapping("/foto")
    public ResponseEntity<RespostaPerfil> removerFoto(Authentication autenticacao) {
        return ResponseEntity.ok(servicoPerfil.removerFoto(autenticacao.getName()));
    }

    /**
     * Exclui o cadastro do usuário autenticado e todos os dados dele. Exige a senha atual.
     *
     * @param autenticacao Autenticação do usuário atual
     * @param requisicao Senha atual
     * @return Resposta vazia com status 204
     */
    @DeleteMapping
    public ResponseEntity<Void> excluirCadastro(Authentication autenticacao,
                                                @Valid @RequestBody RequisicaoExclusaoCadastro requisicao) {
        servicoPerfil.excluirCadastro(autenticacao.getName(), requisicao.senha());
        return ResponseEntity.noContent().build();
    }
}
