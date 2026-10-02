package com.efinanceiro.servico;

import com.efinanceiro.dominio.Cartao;
import com.efinanceiro.dominio.Transacao;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Ciclo da fatura do cartão. A compra feita antes do dia de fechamento entra na fatura que fecha no
 * mesmo mês; a partir do dia de fechamento, na do mês seguinte. A fatura vence no mês em que fecha
 * quando o dia de vencimento vem depois do fechamento, senão no mês seguinte. Dia que o mês não tem
 * (31 em fevereiro, por exemplo) vira o último dia do mês.
 */
public final class CalculadoraFatura {

    private CalculadoraFatura() {
    }

    /**
     * Vencimento da fatura em que cai uma parcela da compra.
     *
     * @param cartao Cartão da compra
     * @param dataCompra Data da compra
     * @param numeroParcela Número da parcela (1 para a primeira ou para compra à vista)
     * @return Data de vencimento da fatura da parcela
     */
    public static LocalDate vencimentoDaParcela(Cartao cartao, LocalDate dataCompra, int numeroParcela) {
        YearMonth fechamento = YearMonth.from(dataCompra);

        if (dataCompra.getDayOfMonth() >= diaNoMes(fechamento, cartao.getDiaFechamento())) {
            fechamento = fechamento.plusMonths(1);
        }

        YearMonth vencimento = cartao.getDiaVencimento() > cartao.getDiaFechamento() ? fechamento : fechamento.plusMonths(1);
        YearMonth vencimentoDaParcela = vencimento.plusMonths(numeroParcela - 1L);

        return vencimentoDaParcela.atDay(diaNoMes(vencimentoDaParcela, cartao.getDiaVencimento()));
    }

    /**
     * Vencimento da fatura anterior à que vence na data informada.
     *
     * @param cartao Cartão
     * @param vencimento Vencimento de uma fatura do cartão
     * @return Vencimento da fatura do mês anterior
     */
    public static LocalDate vencimentoAnterior(Cartao cartao, LocalDate vencimento) {
        YearMonth anterior = YearMonth.from(vencimento).minusMonths(1);
        return anterior.atDay(diaNoMes(anterior, cartao.getDiaVencimento()));
    }

    /**
     * Preenche as datas da transação a partir da data informada pelo usuário. Com cartão, essa data é
     * a da compra e a transação passa a cair no vencimento da fatura da parcela; sem cartão, é a
     * própria data da transação. O cartão e o número da parcela já devem estar preenchidos.
     *
     * @param transacao Transação a preencher
     * @param dataInformada Data informada (da compra, quando há cartão)
     */
    public static void aplicarData(Transacao transacao, LocalDate dataInformada) {
        Cartao cartao = transacao.getCartao();

        if (cartao == null) {
            transacao.setDataCompra(null);
            transacao.setDataTransacao(dataInformada);
            return;
        }

        int parcela = transacao.getNumeroParcela() != null ? transacao.getNumeroParcela() : 1;
        transacao.setDataCompra(dataInformada);
        transacao.setDataTransacao(vencimentoDaParcela(cartao, dataInformada, parcela));
    }

    private static int diaNoMes(YearMonth mes, int dia) {
        return Math.min(dia, mes.lengthOfMonth());
    }
}
