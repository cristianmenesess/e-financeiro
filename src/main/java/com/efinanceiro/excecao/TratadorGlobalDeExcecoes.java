package com.efinanceiro.excecao;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.databind.exc.MismatchedInputException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Slf4j
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
     * Trata erros de validação em parâmetros de query/rota (ex: tamanho de página fora do limite).
     *
     * @param excecao Exceção lançada pela validação de parâmetros do método do controller
     * @return Corpo de erro com a primeira mensagem de validação, com status 400
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<Map<String, Object>> tratarValidacaoDeParametro(HandlerMethodValidationException excecao) {
        String mensagem = excecao.getAllErrors().stream()
                .map(erro -> erro.getDefaultMessage())
                .findFirst()
                .orElse("Parâmetro inválido");

        return ResponseEntity.badRequest().body(corpoDeErro(mensagem));
    }

    /**
     * Trata corpo de requisição ausente ou impossível de ler: JSON malformado, enum com valor que não
     * existe, data em formato inválido, texto num campo numérico.
     *
     * @param excecao Exceção lançada ao converter o corpo da requisição
     * @return Corpo de erro com status 400, apontando o campo quando for possível identificá-lo
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> tratarCorpoIlegivel(HttpMessageNotReadableException excecao) {
        String mensagem = "Corpo da requisição inválido ou ausente";

        if (excecao.getCause() instanceof MismatchedInputException erroDeCampo && !erroDeCampo.getPath().isEmpty()) {
            String campo = erroDeCampo.getPath().get(erroDeCampo.getPath().size() - 1).getPropertyName();

            if (campo != null) {
                mensagem = "Valor inválido para o campo '" + campo + "'";
            }
        }

        return ResponseEntity.badRequest().body(corpoDeErro(mensagem));
    }

    /**
     * Trata parâmetro de query/rota com tipo errado (ex: {@code ?contaId=abc} ou {@code /api/cartoes/abc}).
     *
     * @param excecao Exceção de conversão do parâmetro
     * @return Corpo de erro com status 400
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> tratarTipoDeParametroInvalido(MethodArgumentTypeMismatchException excecao) {
        return ResponseEntity.badRequest().body(corpoDeErro("Valor inválido para o parâmetro '" + excecao.getName() + "'"));
    }

    /**
     * Trata parâmetro obrigatório de query ausente.
     *
     * @param excecao Exceção de parâmetro ausente
     * @return Corpo de erro com status 400
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> tratarParametroAusente(MissingServletRequestParameterException excecao) {
        return ResponseEntity.badRequest().body(corpoDeErro("O parâmetro '" + excecao.getParameterName() + "' é obrigatório"));
    }

    /**
     * Trata rota que não existe na API.
     *
     * @param excecao Exceção de recurso estático/rota não encontrada
     * @return Corpo de erro com status 404
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> tratarRotaInexistente(NoResourceFoundException excecao) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(corpoDeErro("Rota não encontrada"));
    }

    /**
     * Trata chamada a uma rota existente com o método HTTP errado (ex: GET numa rota só de POST).
     *
     * @param excecao Exceção de método não suportado
     * @return Corpo de erro com status 405 e o cabeçalho Allow com os métodos aceitos
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> tratarMetodoNaoSuportado(HttpRequestMethodNotSupportedException excecao) {
        ResponseEntity.BodyBuilder resposta = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED);

        if (excecao.getSupportedHttpMethods() != null) {
            resposta.allow(excecao.getSupportedHttpMethods().toArray(new HttpMethod[0]));
        }

        return resposta.body(corpoDeErro("Método " + excecao.getMethod() + " não é permitido nesta rota"));
    }

    /**
     * Trata corpo enviado num formato que a API não aceita (ex: sem Content-Type: application/json).
     *
     * @param excecao Exceção de tipo de mídia não suportado
     * @return Corpo de erro com status 415
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> tratarTipoDeMidiaNaoSuportado(HttpMediaTypeNotSupportedException excecao) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(corpoDeErro("Envie os dados em formato JSON"));
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
     * Trata tentativa de criar algo que já existe com o mesmo nome (ex: categoria repetida).
     *
     * @param excecao Exceção de recurso duplicado
     * @return Corpo de erro com status 409
     */
    @ExceptionHandler(RecursoDuplicadoException.class)
    public ResponseEntity<Map<String, Object>> tratarRecursoDuplicado(RecursoDuplicadoException excecao) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(corpoDeErro(excecao.getMessage()));
    }

    /**
     * Trata violação de restrição do banco que escapou das validações do serviço (ex: dois cadastros
     * simultâneos com o mesmo e-mail passando juntos pela checagem de duplicidade).
     *
     * @param excecao Exceção de integridade do banco
     * @return Corpo de erro genérico com status 409
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> tratarViolacaoDeIntegridade(DataIntegrityViolationException excecao) {
        log.warn("Violação de integridade no banco: {}", excecao.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(corpoDeErro("A operação conflita com dados já existentes"));
    }

    /**
     * Trata login com credenciais inválidas.
     *
     * @param excecao Exceção de credenciais inválidas ou falha de autenticação do Spring Security
     * @return Corpo de erro com status 401
     */
    @ExceptionHandler({CredenciaisInvalidasException.class, BadCredentialsException.class})
    public ResponseEntity<Map<String, Object>> tratarCredenciaisInvalidas(RuntimeException excecao) {
        String mensagem = excecao instanceof CredenciaisInvalidasException
                ? excecao.getMessage()
                : "E-mail ou senha inválidos";

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(corpoDeErro(mensagem));
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
     * Trata operação válida no formato, mas proibida pela regra de negócio (ex: excluir a única conta).
     *
     * @param excecao Exceção de regra de negócio
     * @return Corpo de erro com status 422
     */
    @ExceptionHandler(RegraDeNegocioException.class)
    public ResponseEntity<Map<String, Object>> tratarRegraDeNegocio(RegraDeNegocioException excecao) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(corpoDeErro(excecao.getMessage()));
    }

    /**
     * Trata excesso de tentativas em rotas sensíveis (login, cadastro, redefinição de senha).
     *
     * @param excecao Exceção de limite de requisições excedido
     * @return Corpo de erro com status 429
     */
    @ExceptionHandler(LimiteDeRequisicoesExcedidoException.class)
    public ResponseEntity<Map<String, Object>> tratarLimiteExcedido(LimiteDeRequisicoesExcedidoException excecao) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(corpoDeErro(excecao.getMessage()));
    }

    /**
     * Trata dado recusado por uma regra do serviço (ex: trocar o e-mail sem informar a senha,
     * foto em formato não aceito).
     *
     * @param excecao Exceção de dados inválidos
     * @return Corpo de erro com status 400
     */
    @ExceptionHandler(DadosInvalidosException.class)
    public ResponseEntity<Map<String, Object>> tratarDadosInvalidos(DadosInvalidosException excecao) {
        return ResponseEntity.badRequest().body(corpoDeErro(excecao.getMessage()));
    }

    /**
     * Trata importação de planilha com erros: devolve a lista de erros (linha, coluna e motivo).
     *
     * @param excecao Exceção com os erros da planilha
     * @return Corpo de erro com a lista de erros, status 400
     */
    @ExceptionHandler(ImportacaoInvalidaException.class)
    public ResponseEntity<Map<String, Object>> tratarImportacaoInvalida(ImportacaoInvalidaException excecao) {
        Map<String, Object> corpo = corpoDeErro(excecao.getMessage());
        corpo.put("erros", excecao.getErros());
        return ResponseEntity.badRequest().body(corpo);
    }

    /**
     * Trata falha de um serviço externo (ex: Cloudinary fora do ar). O detalhe fica no log de quem
     * lançou a exceção; aqui só volta uma mensagem amigável.
     *
     * @param excecao Exceção de serviço externo indisponível
     * @return Corpo de erro com status 502
     */
    @ExceptionHandler(ServicoExternoIndisponivelException.class)
    public ResponseEntity<Map<String, Object>> tratarServicoExternoIndisponivel(ServicoExternoIndisponivelException excecao) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(corpoDeErro(excecao.getMessage()));
    }

    /**
     * Trata upload acima do limite configurado em spring.servlet.multipart (barrado antes de chegar
     * no controller).
     *
     * @param excecao Exceção de tamanho de upload excedido
     * @return Corpo de erro com status 400
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> tratarUploadGrandeDemais(MaxUploadSizeExceededException excecao) {
        return ResponseEntity.badRequest().body(corpoDeErro("A foto deve ter no máximo 5 MB"));
    }

    /**
     * Trata requisição multipart sem o arquivo esperado.
     *
     * @param excecao Exceção de parte do multipart ausente
     * @return Corpo de erro com status 400
     */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<Map<String, Object>> tratarArquivoAusente(MissingServletRequestPartException excecao) {
        return ResponseEntity.badRequest().body(corpoDeErro("Envie o arquivo no campo '" + excecao.getRequestPartName() + "'"));
    }

    /**
     * Trata tentativa de redefinição de senha com token inexistente, já usado ou expirado.
     *
     * @param excecao Exceção de token inválido ou expirado
     * @return Corpo de erro com status 400
     */
    @ExceptionHandler(TokenInvalidoOuExpiradoException.class)
    public ResponseEntity<Map<String, Object>> tratarTokenInvalidoOuExpirado(TokenInvalidoOuExpiradoException excecao) {
        return ResponseEntity.badRequest().body(corpoDeErro(excecao.getMessage()));
    }

    /**
     * Trata qualquer erro não mapeado explicitamente, sem vazar detalhes internos ao cliente — mas
     * registrando a exceção completa no log, pra dar pra investigar depois.
     *
     * @param excecao Exceção não tratada
     * @return Corpo de erro genérico com status 500
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> tratarErroGenerico(Exception excecao) {
        log.error("Erro não tratado", excecao);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(corpoDeErro("Erro interno no servidor"));
    }

    private Map<String, Object> corpoDeErro(String mensagem) {
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("mensagem", mensagem);
        corpo.put("timestamp", Instant.now().toString());
        return corpo;
    }
}
