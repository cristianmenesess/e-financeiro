package com.efinanceiro.seguranca;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
public class FiltroAutenticacaoJwt extends OncePerRequestFilter {

    private final ServicoJwt servicoJwt;
    private final ServicoDetalhesUsuario servicoDetalhesUsuario;

    public FiltroAutenticacaoJwt(ServicoJwt servicoJwt, ServicoDetalhesUsuario servicoDetalhesUsuario) {
        this.servicoJwt = servicoJwt;
        this.servicoDetalhesUsuario = servicoDetalhesUsuario;
    }

    /**
     * Intercepta cada requisição para validar o token JWT e autenticar o usuário no contexto de segurança.
     * Token inválido, expirado, de usuário inexistente ou anterior à última troca de senha não derruba a
     * requisição: ela só segue sem autenticação, e a própria configuração de segurança responde 401 sem
     * corpo (que o front interpreta como sessão expirada).
     *
     * @param request Requisição HTTP
     * @param response Resposta HTTP
     * @param filterChain Cadeia de filtros do Spring Security
     */
    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {

        String cabecalhoAuth = request.getHeader("Authorization");

        if (cabecalhoAuth != null && cabecalhoAuth.startsWith("Bearer ")
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            autenticar(cabecalhoAuth.substring(7), request);
        }

        filterChain.doFilter(request, response);
    }

    private void autenticar(String token, HttpServletRequest request) {
        try {
            Claims claims = servicoJwt.lerClaims(token);
            UsuarioAutenticado usuario = (UsuarioAutenticado) servicoDetalhesUsuario.loadUserByUsername(claims.getSubject());

            if (servicoJwt.emitidoAntesDaTrocaDeSenha(claims, usuario.getSenhaAlteradaEm())) {
                log.info("Token recusado: emitido antes da última troca de senha do usuário {}", usuario.getId());
                return;
            }

            UsernamePasswordAuthenticationToken autenticacao =
                    new UsernamePasswordAuthenticationToken(usuario, null, usuario.getAuthorities());

            autenticacao.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(autenticacao);
        } catch (ExpiredJwtException e) {
            // Caso normal de sessão vencida — não é erro, só não autentica
            log.debug("Token expirado em {}", e.getClaims().getExpiration());
        } catch (JwtException | IllegalArgumentException e) {
            // Token malformado ou com assinatura inválida: pode ser adulteração, vale registrar
            log.warn("Token JWT inválido recusado ({}): {}", request.getRequestURI(), e.getMessage());
        } catch (UsernameNotFoundException e) {
            log.info("Token válido de um usuário que não existe mais");
        }
    }
}
