package com.efinanceiro.dto.validacao;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;

public class ValidadorTamanhoMaximoEmBytes implements ConstraintValidator<TamanhoMaximoEmBytes, String> {

    private int limite;

    @Override
    public void initialize(TamanhoMaximoEmBytes anotacao) {
        this.limite = anotacao.value();
    }

    /**
     * Valida se o texto cabe no limite de bytes. Nulo é considerado válido — obrigatoriedade é
     * responsabilidade do @NotBlank.
     *
     * @param valor Texto a validar
     * @param contexto Contexto do Bean Validation
     * @return true se o texto for nulo ou couber no limite
     */
    @Override
    public boolean isValid(String valor, ConstraintValidatorContext contexto) {
        return valor == null || valor.getBytes(StandardCharsets.UTF_8).length <= limite;
    }
}
