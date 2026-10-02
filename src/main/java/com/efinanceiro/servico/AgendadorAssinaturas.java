package com.efinanceiro.servico;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Completa as cobranças das assinaturas ativas até o horizonte. Roda pouco depois de a aplicação
 * subir e depois a cada 6 horas — no Render free tier a aplicação dorme, então não dá pra contar
 * com um horário fixo. Lançar é idempotente: rodar de novo não duplica cobrança.
 */
@Slf4j
@Component
@Lazy(false)
@ConditionalOnProperty(name = "app.agendamento.habilitado", havingValue = "true", matchIfMissing = true)
public class AgendadorAssinaturas {

    private final ServicoAssinatura servicoAssinatura;

    public AgendadorAssinaturas(ServicoAssinatura servicoAssinatura) {
        this.servicoAssinatura = servicoAssinatura;
    }

    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT6H")
    public void lancarProximasCobrancas() {
        try {
            servicoAssinatura.lancarProximasCobrancasDeTodos();
        } catch (RuntimeException e) {
            log.warn("Falha ao lançar as próximas cobranças das assinaturas", e);
        }
    }
}
