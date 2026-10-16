package com.rabbitlab.backoffice.domain.product;

import com.rabbitlab.backoffice.domain.RuleViolationException;
import com.rabbitlab.backoffice.domain.Sku;

/** Pasif ürün değiştirilemez. Tekrar aktifleştirme kapsam dışı. */
public class ProductInactiveException extends RuleViolationException {

    public ProductInactiveException(Sku sku) {
        super("Ürün pasif, değiştirilemez: " + sku);
    }
}
