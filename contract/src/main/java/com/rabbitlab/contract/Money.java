package com.rabbitlab.contract;

import java.math.BigDecimal;

/** v2'de fiyat: tutar ve para birimi tek nesne. v1'de bunlar mesajın iki düz alanıydı. */
public record Money(BigDecimal amount, String currency) {
}
