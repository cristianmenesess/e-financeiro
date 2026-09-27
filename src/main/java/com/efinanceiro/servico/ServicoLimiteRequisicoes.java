package com.efinanceiro.servico;

import com.efinanceiro.excecao.LimiteDeRequisicoesExcedidoException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limite de tentativas nas rotas públicas sensíveis (login, cadastro, esqueci/redefinir senha),
 * contra força bruta de senha e contra esgotar a cota de e-mails do Resend. Usa Bucket4j em memória
 * (um "balde" por regra + IP ou e-mail): com uma instância só no Render, não precisa de Redis.
 */
@Slf4j
@Service
public class ServicoLimiteRequisicoes {

    private static final int LIMPAR_ACIMA_DE = 10_000;

    private final Map<String, Bucket> baldes = new ConcurrentHashMap<>();

    /**
     * Regras de limite por tipo de ação: capacidade de tentativas dentro da janela de tempo.
     */
    public enum Regra {
        LOGIN_POR_IP(30, Duration.ofMinutes(5)),
        LOGIN_POR_EMAIL(10, Duration.ofMinutes(15)),
        CADASTRO_POR_IP(10, Duration.ofHours(1)),
        ESQUECI_SENHA_POR_IP(10, Duration.ofHours(1)),
        ESQUECI_SENHA_POR_EMAIL(3, Duration.ofHours(1)),
        REDEFINIR_SENHA_POR_IP(20, Duration.ofMinutes(15)),
        SENHA_ATUAL_POR_USUARIO(5, Duration.ofMinutes(15));

        private final int tentativas;
        private final Duration janela;

        Regra(int tentativas, Duration janela) {
            this.tentativas = tentativas;
            this.janela = janela;
        }
    }

    /**
     * Consome uma tentativa da regra para a chave informada (IP ou e-mail) e bloqueia se o limite
     * já foi atingido.
     *
     * @param regra Regra de limite a aplicar
     * @param chave IP do cliente ou e-mail informado
     * @throws LimiteDeRequisicoesExcedidoException se não houver mais tentativas disponíveis
     */
    public void consumir(Regra regra, String chave) {
        limparBaldesCheios();

        Bucket balde = baldes.computeIfAbsent(regra.name() + ":" + chave, k -> criarBalde(regra));

        if (!balde.tryConsume(1)) {
            log.warn("Limite de requisições atingido: {} ({})", regra, chave);
            throw new LimiteDeRequisicoesExcedidoException("Muitas tentativas. Aguarde alguns minutos e tente de novo.");
        }
    }

    private Bucket criarBalde(Regra regra) {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(regra.tentativas)
                        .refillGreedy(regra.tentativas, regra.janela)
                        .build())
                .build();
    }

    // Balde cheio é igual a um balde novo — descartar não perde nenhuma contagem e impede o mapa
    // de crescer sem limite com IPs/e-mails que só apareceram uma vez
    private void limparBaldesCheios() {
        if (baldes.size() > LIMPAR_ACIMA_DE) {
            baldes.entrySet().removeIf(entrada -> {
                Regra regra = Regra.valueOf(entrada.getKey().substring(0, entrada.getKey().indexOf(':')));
                return entrada.getValue().getAvailableTokens() >= regra.tentativas;
            });
        }
    }
}
