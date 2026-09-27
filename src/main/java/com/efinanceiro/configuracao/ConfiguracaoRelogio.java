package com.efinanceiro.configuracao;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class ConfiguracaoRelogio {

    /**
     * Relógio único da aplicação, com o fuso horário do usuário (não o do servidor). O Render roda
     * em UTC: sem isso, a partir das 21h de Brasília o "hoje" do servidor já é o dia seguinte e o
     * saldo passa a contar parcelas de amanhã. Injetar o Clock (em vez de mudar o fuso da JVM)
     * também deixa as regras de data testáveis com um relógio fixo.
     *
     * @param fusoHorario Fuso horário usado para calcular "hoje" (ex: America/Sao_Paulo)
     * @return Relógio do sistema no fuso configurado
     */
    @Bean
    public Clock relogio(@Value("${app.fuso-horario}") String fusoHorario) {
        return Clock.system(ZoneId.of(fusoHorario));
    }
}
