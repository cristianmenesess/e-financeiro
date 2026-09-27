package com.efinanceiro.dto.validacao;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Limita o tamanho de um texto em bytes UTF-8 (não em caracteres). Existe por causa do BCrypt,
 * que só aceita senhas de até 72 bytes: um @Size(max = 72) deixaria passar 72 caracteres
 * acentuados (que ocupam mais de 72 bytes) e o cadastro quebraria com erro 500.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidadorTamanhoMaximoEmBytes.class)
public @interface TamanhoMaximoEmBytes {

    int value();

    String message();

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
