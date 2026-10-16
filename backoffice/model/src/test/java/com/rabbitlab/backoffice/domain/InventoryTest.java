package com.rabbitlab.backoffice.domain;

import com.rabbitlab.backoffice.domain.event.StockUpdated;
import com.rabbitlab.backoffice.domain.inventory.Inventory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P2-M1 — Inventory aggregate'i. Stok, Product'tan ayrı bir aggregate:
 * <ul>
 *   <li>farklı aktör değiştirir (depo görevlisi / ürün yöneticisi),</li>
 *   <li>kendi versiyon sayacı var (Faz 1'deki iki ayrı versiyonun sebebi),</li>
 *   <li>fiyat değişikliği ile stok sayımı aynı anda yapılırsa birbirini kilitlemez.</li>
 * </ul>
 */
class InventoryTest {

    private static final Sku SKU = new Sku("SKU-1");

    @Test
    void should_open_empty_without_event() {
        // Ürün "Tükendi" olarak doğar; ilk stok StockUpdated ile gelir (senaryo kararı).
        Inventory inventory = Inventory.open(SKU);

        assertThat(inventory.quantity()).isZero();
        assertThat(inventory.version()).isZero();
        assertThat(inventory.pullEvents()).isEmpty();
    }

    @Test
    void should_record_absolute_quantity_with_next_version() {
        Inventory inventory = Inventory.open(SKU);

        inventory.count(5);
        inventory.count(8);

        assertThat(inventory.quantity()).isEqualTo(8);
        assertThat(inventory.pullEvents())
                .containsExactly(new StockUpdated(SKU, 5, 1), new StockUpdated(SKU, 8, 2));
    }

    @Test
    void should_not_change_version_when_count_is_same() {
        Inventory inventory = Inventory.open(SKU);
        inventory.count(5);
        inventory.pullEvents();

        inventory.count(5);

        assertThat(inventory.version()).isEqualTo(1);
        assertThat(inventory.pullEvents()).isEmpty();
    }

    @Test
    void should_reject_negative_quantity() {
        Inventory inventory = Inventory.open(SKU);

        assertThatThrownBy(() -> inventory.count(-5))
                .isInstanceOf(InvalidValueException.class)
                .hasMessageContaining("-5");
        assertThat(inventory.pullEvents()).isEmpty();
    }
}
