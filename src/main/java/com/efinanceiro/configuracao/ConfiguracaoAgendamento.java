package com.efinanceiro.configuracao;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Liga as tarefas agendadas. Os testes desligam (app.agendamento.habilitado=false) pra rodar com o
 * relógio controlado sem nada mexendo no banco por fora.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.agendamento.habilitado", havingValue = "true", matchIfMissing = true)
public class ConfiguracaoAgendamento {
}
