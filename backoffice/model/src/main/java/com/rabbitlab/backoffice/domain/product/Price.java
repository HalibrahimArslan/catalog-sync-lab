package com.rabbitlab.backoffice.domain.product;

import com.rabbitlab.backoffice.domain.InvalidValueException;
import com.rabbitlab.backoffice.domain.Require;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Fiyat: tutar + para birimi. Value object: kimliği yok, değeriyle eşittir.
 *
 * <p>Tutar her zaman 2 ondalığa sabitlenir. Böylece {@code 100} ile {@code 100.00} eşit olur
 * ({@code BigDecimal.equals} ölçeğe duyarlıdır, 100 ≠ 100.00). Daha fazla ondalık reddedilir;
 * sessizce yuvarlamak para kaybettirir.
 */
public record Price(BigDecimal amount, String currency) {

    private static final int SCALE = 2;

    public Price {
        Require.notNull(amount, "price");
        Require.notBlank(currency, "currency");
        if (amount.signum() <= 0) {
            throw new InvalidValueException("price sıfırdan büyük olmalı: " + amount);
        }
        try {
            amount = amount.setScale(SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new InvalidValueException("price en fazla " + SCALE + " ondalık olabilir: " + amount);
        }
    }
}
