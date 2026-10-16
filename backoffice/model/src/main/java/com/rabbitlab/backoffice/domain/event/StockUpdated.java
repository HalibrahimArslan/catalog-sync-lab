package com.rabbitlab.backoffice.domain.event;

import com.rabbitlab.backoffice.domain.Sku;

/** Stok MUTLAK değerle bildirilir ("artık 8"); {@code version} Inventory'nin versiyonudur. */
public record StockUpdated(Sku sku, int quantity, long version) implements DomainEvent {
}
