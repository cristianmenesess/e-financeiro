package com.efinanceiro.excecao;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class TratadorGlobalDeExcecoes {

    /**
     * Trata erros de validação dos DTOs de requisição, retornando uma mensagem por campo inválido.
     *
     * @param excecao Exceção lançada pelo Bean Validation
     * @return Mapa de campo para mensagem de erro, com status 400
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> tratarValidacao(MethodArgumentNotValidException excecao) {
        Map<String, String> erros = new HashMap<>();

        excecao.getBindingResult().getFieldErrors()
                .forEach(erro -> erros.put(erro.getField(), erro.getDefaultMessage()));

        return ResponseEntity.badRequest().body(erros);
    }

    /**
     * Trata tentativa de cadastro com e-mail já existente.
     *
     * @param excecao Exceção de e-mail duplicado
     * @return Corpo de erro com status 409
     */
    @ExceptionHandler(EmailJaCadastradoException.class)
    public ResponseEntity<Map<String, Object>> tratarEmailJaCadastrado(EmailJaCadastradoException excecao) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(corpoDeErro(excecao.getMessage()));
    }

    /**
     * Trata login com credenciais inválidas.
     *
     * @param excecao Exceção de credenciais inválidas ou falha de autenticação do Spring Security
     * @return Corpo de erro com status 401
     */
    @ExceptionHandler({CredenciaisInvalidasException.class, BadCredentialsException.class})
    public ResponseEntity<Map<String, Object>> tratarCredenciaisInvalidas(RuntimeException excecao) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(corpoDeErro("E-mail ou senha inválidos"));
    }

    /**
     * Trata tentativa de acesso a um recurso que não existe ou não pertence ao usuário autenticado.
     *
     * @param excecao Exceção de recurso não encontrado
     * @return Corpo de erro com status 404
     */
    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<Map<String, Object>> tratarRecursoNaoEncontrado(RecursoNaoEncontradoException excecao) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(corpoDeErro(excecao.getMessage()));
    }

    /**
     * Trata qualquer erro não mapeado explicitamente, sem vazar detalhes internos ao cliente.
     *
     * @param excecao Exceção não tratada
     * @return Corpo de erro genérico com status 500
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> tratarErroGenerico(Exception excecao) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(corpoDeErro("Erro interno no servidor"));
    }

    private Map<String, Object> corpoDeErro(String mensagem) {
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("mensagem", mensagem);
        corpo.put("timestamp", Instant.now().toString());
        return corpo;
    }
}
