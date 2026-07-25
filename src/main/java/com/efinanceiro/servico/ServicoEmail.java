package com.efinanceiro.servico;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Service
public class ServicoEmail {

    private final RestClient restClient;
    private final String remetente;

    public ServicoEmail(@Value("${app.email.resend.api-key}") String apiKey,
                         @Value("${app.email.remetente}") String remetente) {
        this.remetente = remetente;
        this.restClient = RestClient.builder()
                .baseUrl("https://api.resend.com")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    /**
     * Envia o e-mail de redefinição de senha com o link contendo o token de reset.
     *
     * @param destinatario E-mail do usuário que solicitou a redefinição
     * @param nomeDestinatario Nome do usuário, usado na saudação do e-mail
     * @param linkRedefinicao Link completo (com o token) para a tela de redefinição de senha
     */
    public void enviarEmailRedefinicaoSenha(String destinatario, String nomeDestinatario, String linkRedefinicao) {
        String html = """
                <p>Olá, %s!</p>
                <p>Recebemos uma solicitação para redefinir sua senha no E-Financeiro.</p>
                <p><a href="%s">Clique aqui para redefinir sua senha</a></p>
                <p>Esse link expira em 1 hora. Se você não solicitou essa alteração, ignore este e-mail.</p>
                """.formatted(nomeDestinatario, linkRedefinicao);

        restClient.post()
                .uri("/emails")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "from", remetente,
                        "to", destinatario,
                        "subject", "Redefinição de senha — E-Financeiro",
                        "html", html
                ))
                .retrieve()
                .toBodilessEntity();
    }
}
