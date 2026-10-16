package com.rabbitlab.backoffice.domainservice;

import com.rabbitlab.backoffice.domain.RuleViolationException;
import com.rabbitlab.backoffice.domain.Sku;

/** SKU benzersizdir. {@link ProductRegistration} korur; aynı anda gelen iki kayıtta son sözü veritabanı söyler. */
public class DuplicateSkuException extends RuleViolationException {

    public DuplicateSkuException(Sku sku) {
        super("Bu SKU ile ürün zaten var: " + sku);
    }
}
