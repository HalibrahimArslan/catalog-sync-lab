package com.rabbitlab.backoffice.domain.event;

import com.rabbitlab.backoffice.domain.Sku;

public record ProductDeactivated(Sku sku, long version) implements DomainEvent {
}
