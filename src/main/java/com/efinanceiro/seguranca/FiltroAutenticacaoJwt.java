package com.efinanceiro.seguranca;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

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

        if (cabecalhoAuth == null || !cabecalhoAuth.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = cabecalhoAuth.substring(7);
        String email = servicoJwt.extrairEmail(token);

        if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails usuario = servicoDetalhesUsuario.loadUserByUsername(email);

            if (servicoJwt.tokenValido(token, usuario.getUsername())) {
                UsernamePasswordAuthenticationToken autenticacao =
                        new UsernamePasswordAuthenticationToken(usuario, null, usuario.getAuthorities());

                autenticacao.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(autenticacao);
            }
        }

        filterChain.doFilter(request, response);
    }
}
