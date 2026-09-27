package com.efinanceiro.seguranca;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Service
public class ServicoJwt {

    private final SecretKey chaveAssinatura;
    private final long expiracaoMs;
    private final Clock relogio;
    private final JwtParser leitor;

    public ServicoJwt(@Value("${app.jwt.secret}") String segredo,
                       @Value("${app.jwt.expiracao-ms}") long expiracaoMs,
                       Clock relogio) {
        this.chaveAssinatura = Keys.hmacShaKeyFor(segredo.getBytes(StandardCharsets.UTF_8));
        this.expiracaoMs = expiracaoMs;
        this.relogio = relogio;
        this.leitor = Jwts.parser()
                .verifyWith(chaveAssinatura)
                .clock(() -> Date.from(relogio.instant()))
                .build();
    }

    /**
     * Gera um token JWT assinado para o e-mail informado.
     *
     * @param email E-mail do usuário autenticado, usado como subject do token
     * @return Token JWT assinado
     */
    public String gerarToken(String email) {
        Date agora = Date.from(relogio.instant());
        Date expiracao = new Date(agora.getTime() + expiracaoMs);

        return Jwts.builder()
                .subject(email)
                .issuedAt(agora)
                .expiration(expiracao)
                .signWith(chaveAssinatura)
                .compact();
    }

    /**
     * Lê as claims de um token, validando assinatura e expiração.
     *
     * @param token Token JWT
     * @return Claims do token (subject = e-mail, issuedAt = emissão)
     * @throws JwtException se o token estiver malformado, com assinatura inválida ou expirado
     */
    public Claims lerClaims(String token) {
        return leitor.parseSignedClaims(token).getPayload();
    }

    /**
     * Verifica se o token foi emitido antes da última mudança de credenciais do usuário (troca de
     * senha, troca de e-mail ou o próprio cadastro) — nesse caso ele não vale mais. Isso cobre tanto
     * quem roubou o token e perde o acesso quando a senha é redefinida, quanto o e-mail do subject
     * do token ter sido liberado por essa conta (troca ou exclusão) e reaproveitado por outra: toda
     * conta nova já sai do cadastro com esse campo preenchido, então um token antigo nunca vale pra
     * quem pegou o e-mail depois. Compara
     * em segundos porque o "iat" do JWT não guarda milissegundos: sem isso, um login feito no mesmo
     * segundo da mudança seria recusado.
     *
     * @param claims Claims do token já validado
     * @param senhaAlteradaEm Momento da última mudança de credenciais, ou null em usuário antigo que nunca trocou senha nem e-mail
     * @return true se o token for anterior à mudança de credenciais
     */
    public boolean emitidoAntesDaTrocaDeSenha(Claims claims, Instant senhaAlteradaEm) {
        if (senhaAlteradaEm == null || claims.getIssuedAt() == null) {
            return false;
        }

        return claims.getIssuedAt().toInstant().isBefore(senhaAlteradaEm.truncatedTo(ChronoUnit.SECONDS));
    }
}
