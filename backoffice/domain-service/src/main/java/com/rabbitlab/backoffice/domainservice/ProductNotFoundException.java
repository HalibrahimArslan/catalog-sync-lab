package com.rabbitlab.backoffice.domainservice;

import com.rabbitlab.backoffice.domain.NotFoundException;
import com.rabbitlab.backoffice.domain.Sku;

public class ProductNotFoundException extends NotFoundException {

    public ProductNotFoundException(Sku sku) {
        super("Ürün bulunamadı: " + sku);
    }
}
