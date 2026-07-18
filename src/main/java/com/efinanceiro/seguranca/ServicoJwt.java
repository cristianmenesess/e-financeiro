package com.efinanceiro.seguranca;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.function.Function;

@Service
public class ServicoJwt {

    private final SecretKey chaveAssinatura;
    private final long expiracaoMs;

    public ServicoJwt(@Value("${app.jwt.secret}") String segredo,
                       @Value("${app.jwt.expiracao-ms}") long expiracaoMs) {
        this.chaveAssinatura = Keys.hmacShaKeyFor(segredo.getBytes(StandardCharsets.UTF_8));
        this.expiracaoMs = expiracaoMs;
    }

    /**
     * Gera um token JWT assinado para o e-mail informado.
     *
     * @param email E-mail do usuário autenticado, usado como subject do token
     * @return Token JWT assinado
     */
    public String gerarToken(String email) {
        Date agora = new Date();
        Date expiracao = new Date(agora.getTime() + expiracaoMs);

        return Jwts.builder()
                .subject(email)
                .issuedAt(agora)
                .expiration(expiracao)
                .signWith(chaveAssinatura)
                .compact();
    }

    /**
     * Extrai o e-mail (subject) contido no token.
     *
     * @param token Token JWT
     * @return E-mail do usuário
     */
    public String extrairEmail(String token) {
        return extrairClaim(token, Claims::getSubject);
    }

    /**
     * Verifica se o token é válido para o e-mail informado (assinatura correta e não expirado).
     *
     * @param token Token JWT
     * @param email E-mail esperado
     * @return true se o token for válido
     */
    public boolean tokenValido(String token, String email) {
        String emailDoToken = extrairEmail(token);
        return emailDoToken.equals(email) && !tokenExpirado(token);
    }

    private boolean tokenExpirado(String token) {
        return extrairClaim(token, Claims::getExpiration).before(new Date());
    }

    private <T> T extrairClaim(String token, Function<Claims, T> resolvedor) {
        Claims claims = Jwts.parser()
                .verifyWith(chaveAssinatura)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return resolvedor.apply(claims);
    }
}
