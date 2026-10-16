package com.rabbitlab.backoffice.application;

import com.rabbitlab.backoffice.domain.inventory.Inventory;
import com.rabbitlab.backoffice.domainservice.InventoryRepository;

/**
 * Stok use case'i. Product'ı hiç yüklemez: ayrı aggregate olmanın faydası, fiyat güncellemesi ile
 * stok sayımı aynı satırı kilitlemez.
 */
public final class InventoryService {

    private final InventoryRepository inventories;
    private final EventOutbox outbox;
    private final Transaction transaction;

    public InventoryService(InventoryRepository inventories, EventOutbox outbox, Transaction transaction) {
        this.inventories = inventories;
        this.outbox = outbox;
        this.transaction = transaction;
    }

    public void count(CountStockCommand command) {
        transaction.execute(() -> {
            Inventory inventory = inventories.get(command.sku());
            inventory.count(command.quantity());
            var events = inventory.pullEvents();
            if (events.isEmpty()) {
                return;
            }
            inventories.save(inventory);
            outbox.append(events);
        });
    }
}
