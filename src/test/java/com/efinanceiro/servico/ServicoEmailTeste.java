package com.efinanceiro.servico;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ServicoEmailTeste {

    private final ServicoEmail servicoEmail = new ServicoEmail("chave-de-teste", "remetente@teste.com");

    @Test
    void nomeComHtmlEEscapadoNoCorpoDoEmail() {
        String html = servicoEmail.montarHtmlRedefinicaoSenha(
                "<a href=\"https://site-falso.com\">Clique aqui</a>",
                "https://e-financeiro.vercel.app/redefinir-senha.html?token=abc123");

        assertThat(html).doesNotContain("<a href=\"https://site-falso.com\">");
        assertThat(html).contains("&lt;a href=&quot;https://site-falso.com&quot;&gt;Clique aqui&lt;/a&gt;");
        assertThat(html).contains("<a href=\"https://e-financeiro.vercel.app/redefinir-senha.html?token=abc123\">");
    }
}
