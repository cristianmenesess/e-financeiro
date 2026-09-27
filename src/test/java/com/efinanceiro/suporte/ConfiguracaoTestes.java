package com.efinanceiro.suporte;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.ZoneId;

@TestConfiguration(proxyBeanMethods = false)
public class ConfiguracaoTestes {

    /**
     * Postgres real (mesma família do Neon), com as migrations do Flyway aplicadas pela aplicação.
     */
    @Bean
    @ServiceConnection
    public PostgreSQLContainer bancoDeTeste() {
        return new PostgreSQLContainer("postgres:17-alpine")
                .withDatabaseName("efinanceiro")
                .withUsername("teste")
                .withPassword("teste");
    }

    /**
     * Substitui o relógio real, com o mesmo fuso de produção.
     */
    @Bean
    @Primary
    public RelogioDeTeste relogioDeTeste() {
        return new RelogioDeTeste(TesteIntegracao.INSTANTE_PADRAO, ZoneId.of("America/Sao_Paulo"));
    }
}
