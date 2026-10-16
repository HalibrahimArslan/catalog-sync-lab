package com.rabbitlab.backoffice.domainservice;

import com.rabbitlab.backoffice.domain.Sku;
import com.rabbitlab.backoffice.domain.inventory.Inventory;

import java.util.Optional;

public interface InventoryRepository {

    Optional<Inventory> findBySku(Sku sku);

    /**
     * Stok, ürünle birlikte açılır ({@link ProductRegistration}); stok yoksa ürün de yoktur.
     *
     * @throws ProductNotFoundException stok (dolayısıyla ürün) yoksa
     */
    default Inventory get(Sku sku) {
        return findBySku(sku).orElseThrow(() -> new ProductNotFoundException(sku));
    }

    /** @throws ConcurrentUpdateException stok okunduktan sonra başkası tarafından değiştirildiyse */
    void save(Inventory inventory);
}
