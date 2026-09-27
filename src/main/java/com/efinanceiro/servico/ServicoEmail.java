package com.efinanceiro.servico;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.HtmlUtils;

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
     * @throws org.springframework.web.client.RestClientException se o Resend recusar ou não responder
     */
    public void enviarEmailRedefinicaoSenha(String destinatario, String nomeDestinatario, String linkRedefinicao) {
        restClient.post()
                .uri("/emails")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "from", remetente,
                        "to", destinatario,
                        "subject", "Redefinição de senha — E-Financeiro",
                        "html", montarHtmlRedefinicaoSenha(nomeDestinatario, linkRedefinicao)
                ))
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * Monta o HTML do e-mail de redefinição. O nome vem do cadastro (texto livre do usuário) e é
     * escapado: sem isso, quem cadastrasse o e-mail de outra pessoa com HTML no nome faria a vítima
     * receber um link falso dentro de um e-mail legítimo do E-Financeiro.
     *
     * @param nomeDestinatario Nome do usuário
     * @param linkRedefinicao Link de redefinição
     * @return HTML do corpo do e-mail
     */
    String montarHtmlRedefinicaoSenha(String nomeDestinatario, String linkRedefinicao) {
        return """
                <p>Olá, %s!</p>
                <p>Recebemos uma solicitação para redefinir sua senha no E-Financeiro.</p>
                <p><a href="%s">Clique aqui para redefinir sua senha</a></p>
                <p>Esse link expira em 1 hora. Se você não solicitou essa alteração, ignore este e-mail.</p>
                """.formatted(HtmlUtils.htmlEscape(nomeDestinatario), HtmlUtils.htmlEscape(linkRedefinicao));
    }
}
