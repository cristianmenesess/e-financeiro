package com.efinanceiro.controlador;

import com.efinanceiro.suporte.TesteIntegracao;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes de acesso ao health check da aplicação.
 */
class HealthCheckTeste extends TesteIntegracao {

    @Test
    void livenessRespondeSemTokenESemDetalhes() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());

        mockMvc.perform(head("/actuator/health/liveness"))
                .andExpect(status().isOk());
    }

    @Test
    void restoDoActuatorContinuaProtegido() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isUnauthorized());
    }
}
