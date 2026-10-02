package com.efinanceiro.servico;

import com.efinanceiro.dominio.Cartao;
import com.efinanceiro.dominio.Transacao;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class CalculadoraFaturaTeste {

    private Cartao cartao(int diaFechamento, int diaVencimento) {
        Cartao cartao = new Cartao();
        cartao.setDiaFechamento(diaFechamento);
        cartao.setDiaVencimento(diaVencimento);
        return cartao;
    }

    private LocalDate vencimento(Cartao cartao, String dataCompra) {
        return CalculadoraFatura.vencimentoDaParcela(cartao, LocalDate.parse(dataCompra), 1);
    }

    @Test
    void compraNoDiaDoFechamentoJaVaiParaAProximaFatura() {
        Cartao cartao = cartao(3, 10);

        assertThat(vencimento(cartao, "2026-03-02")).isEqualTo("2026-03-10");
        assertThat(vencimento(cartao, "2026-03-03")).isEqualTo("2026-04-10");
        assertThat(vencimento(cartao, "2026-03-31")).isEqualTo("2026-04-10");
    }

    @Test
    void vencimentoAntesDoFechamentoFicaNoMesSeguinteAoFechamento() {
        Cartao cartao = cartao(25, 5);

        assertThat(vencimento(cartao, "2026-03-24")).isEqualTo("2026-04-05");
        assertThat(vencimento(cartao, "2026-03-25")).isEqualTo("2026-05-05");
        assertThat(vencimento(cartao, "2026-12-26")).isEqualTo("2027-02-05");
    }

    @Test
    void diaQueOMesNaoTemViraOUltimoDiaDoMes() {
        Cartao fechaDia31 = cartao(31, 8);

        assertThat(vencimento(fechaDia31, "2026-02-27")).isEqualTo("2026-03-08");
        assertThat(vencimento(fechaDia31, "2026-02-28")).isEqualTo("2026-04-08");

        Cartao venceDia31 = cartao(20, 31);
        LocalDate compra = LocalDate.parse("2026-01-10");

        assertThat(CalculadoraFatura.vencimentoDaParcela(venceDia31, compra, 1)).isEqualTo("2026-01-31");
        assertThat(CalculadoraFatura.vencimentoDaParcela(venceDia31, compra, 2)).isEqualTo("2026-02-28");
        assertThat(CalculadoraFatura.vencimentoDaParcela(venceDia31, compra, 3)).isEqualTo("2026-03-31");
        assertThat(CalculadoraFatura.vencimentoAnterior(venceDia31, LocalDate.parse("2026-03-31"))).isEqualTo("2026-02-28");
    }

    @Test
    void parcelasVencemUmaEmCadaFaturaAtravessandoOAno() {
        Cartao cartao = cartao(3, 10);
        LocalDate compra = LocalDate.parse("2026-11-15");

        assertThat(CalculadoraFatura.vencimentoDaParcela(cartao, compra, 1)).isEqualTo("2026-12-10");
        assertThat(CalculadoraFatura.vencimentoDaParcela(cartao, compra, 2)).isEqualTo("2027-01-10");
        assertThat(CalculadoraFatura.vencimentoDaParcela(cartao, compra, 3)).isEqualTo("2027-02-10");
    }

    @Test
    void aplicarDataUsaOVencimentoSoQuandoHaCartao() {
        Transacao semCartao = new Transacao();
        CalculadoraFatura.aplicarData(semCartao, LocalDate.parse("2026-03-14"));

        assertThat(semCartao.getDataTransacao()).isEqualTo("2026-03-14");
        assertThat(semCartao.getDataCompra()).isNull();

        Transacao parcela = new Transacao();
        parcela.setCartao(cartao(3, 10));
        parcela.setNumeroParcela(2);
        CalculadoraFatura.aplicarData(parcela, LocalDate.parse("2026-03-14"));

        assertThat(parcela.getDataTransacao()).isEqualTo("2026-05-10");
        assertThat(parcela.getDataCompra()).isEqualTo("2026-03-14");
    }
}
