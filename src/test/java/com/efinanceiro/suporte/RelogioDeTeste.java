package com.efinanceiro.suporte;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * Relógio ajustável pros testes: permite simular "22h30 em Brasília" (já dia seguinte em UTC),
 * token expirado, troca de senha depois do login, etc., sem depender da hora real da máquina.
 */
public class RelogioDeTeste extends Clock {

    private final ZoneId zona;
    private volatile Instant instante;

    public RelogioDeTeste(Instant instante, ZoneId zona) {
        this.instante = instante;
        this.zona = zona;
    }

    public void definir(Instant novoInstante) {
        this.instante = novoInstante;
    }

    public void avancar(Duration duracao) {
        this.instante = this.instante.plus(duracao);
    }

    @Override
    public ZoneId getZone() {
        return zona;
    }

    @Override
    public Clock withZone(ZoneId novaZona) {
        return new RelogioDeTeste(instante, novaZona);
    }

    @Override
    public Instant instant() {
        return instante;
    }
}
