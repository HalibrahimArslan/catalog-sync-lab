package com.rabbitlab.backoffice.application;

import com.rabbitlab.backoffice.application.CreateProductCommand;
import com.rabbitlab.backoffice.application.UpdateProductCommand;
import com.rabbitlab.backoffice.domain.Sku;
import com.rabbitlab.backoffice.domain.event.ProductCreated;
import com.rabbitlab.backoffice.domain.event.ProductDeactivated;
import com.rabbitlab.backoffice.domain.event.ProductUpdated;
import com.rabbitlab.backoffice.domain.product.Price;
import com.rabbitlab.backoffice.domainservice.DuplicateSkuException;
import com.rabbitlab.backoffice.domainservice.InMemoryInventoryRepository;
import com.rabbitlab.backoffice.domainservice.InMemoryProductRepository;
import com.rabbitlab.backoffice.domainservice.ProductNotFoundException;
import com.rabbitlab.backoffice.domainservice.ProductRegistration;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Ürün use case'leri. P3-M2: inbound port arayüzleri yok; test de REST de application servisini
 * doğrudan çağırıyor. Faz 2'de her use case için ayrı bir arayüz vardı (acıtan nokta 1).
 */
class ProductServiceTest {

    private static final Sku SKU = new Sku("SKU-1");
    private static final Price PRICE = new Price(new BigDecimal("100"), "TRY");

    private final InMemoryProductRepository products = new InMemoryProductRepository();
    private final InMemoryInventoryRepository inventories = new InMemoryInventoryRepository();
    private final RecordingOutbox outbox = new RecordingOutbox();
    private final ProductService service = new ProductService(new ProductRegistration(products), products, inventories, outbox,
            new DirectTransaction());


    @Test
    void should_save_product_with_empty_inventory_and_write_event_to_outbox() {
        service.create(new CreateProductCommand(SKU, "Kupa", "Seramik kupa", PRICE));

        assertThat(products.saved).containsKey(SKU);
        assertThat(inventories.saved.get(SKU).quantity()).isZero();
        assertThat(outbox.events).containsExactly(new ProductCreated(SKU, "Kupa", "Seramik kupa", PRICE, 1));
    }

    @Test
    void should_reject_duplicate_sku_without_writing_event() {
        service.create(new CreateProductCommand(SKU, "Kupa", "Seramik kupa", PRICE));
        outbox.events.clear();

        assertThatThrownBy(() -> service.create(new CreateProductCommand(SKU, "Başka", null, PRICE)))
                .isInstanceOf(DuplicateSkuException.class)
                .hasMessageContaining("SKU-1");
        assertThat(outbox.events).isEmpty();
        assertThat(products.saved.get(SKU).name()).isEqualTo("Kupa");
    }

    @Test
    void should_write_updated_event_when_details_change() {
        service.create(new CreateProductCommand(SKU, "Kupa", "Seramik kupa", PRICE));
        outbox.events.clear();
        Price newPrice = new Price(new BigDecimal("120"), "TRY");

        service.update(new UpdateProductCommand(SKU, "Kupa", "Seramik kupa", newPrice));

        assertThat(outbox.events).containsExactly(new ProductUpdated(SKU, "Kupa", "Seramik kupa", newPrice, 2));
    }

    @Test
    void should_write_deactivated_event() {
        service.create(new CreateProductCommand(SKU, "Kupa", "Seramik kupa", PRICE));
        outbox.events.clear();

        service.deactivate(SKU);

        assertThat(outbox.events).containsExactly(new ProductDeactivated(SKU, 2));
    }

    @Test
    void should_fail_when_updating_unknown_product() {
        assertThatThrownBy(() -> service.update(new UpdateProductCommand(SKU, "Kupa", null, PRICE)))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining("SKU-1");
    }

    @Test
    void should_fail_when_deactivating_unknown_product() {
        assertThatThrownBy(() -> service.deactivate(SKU)).isInstanceOf(ProductNotFoundException.class);
    }
}
