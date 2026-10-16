package com.rabbitlab.storefront.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Vitrinde gösterilen fiyat. Tutar 2 ondalığa sabitlenir; 100 ile 100.00 eşittir. */
public record Price(BigDecimal amount, String currency) {

    public Price {
        if (amount == null || amount.signum() <= 0) {
            throw new InvalidValueException("price sıfırdan büyük olmalı: " + amount);
        }
        if (currency == null || currency.isBlank()) {
            throw new InvalidValueException("currency boş olamaz");
        }
        try {
            amount = amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new InvalidValueException("price en fazla 2 ondalık olabilir: " + amount);
        }
    }
}
