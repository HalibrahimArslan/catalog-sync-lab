package com.rabbitlab.backoffice.domain.event;

import com.rabbitlab.backoffice.domain.Sku;
import com.rabbitlab.backoffice.domain.product.Price;

public record ProductUpdated(Sku sku, String name, String description, Price price, long version)
        implements DomainEvent {
}
