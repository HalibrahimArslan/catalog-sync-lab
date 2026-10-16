package com.rabbitlab.backoffice.application;

import com.rabbitlab.backoffice.domain.Sku;
import com.rabbitlab.backoffice.domain.product.Price;

public record CreateProductCommand(Sku sku, String name, String description, Price price) {
}
