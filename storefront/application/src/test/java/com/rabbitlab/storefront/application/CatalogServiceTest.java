package com.rabbitlab.storefront.application;

import com.rabbitlab.storefront.domain.Price;
import com.rabbitlab.storefront.domain.Sku;
import com.rabbitlab.storefront.domainservice.UnknownProductException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Kataloğu güncelleyen application servisi. Girdi {@link CatalogUpdate}: Storefront'un kendi dilinde
 * "olanlar", ve o değişikliğin kimliği.
 *
 * <p>P3-M2: "Bu değişiklik daha önce işlendi mi?" sorusu (inbox) artık servisin içinde. Faz 2'de
 * listener soruyordu ve use case'in transaction'ını dıştan sarıyordu (acıtan nokta 2).
 *
 * <p>P3-M3: Servis sadece yazar. Okumak için {@code CatalogQueries} var; sonucu burada sahte
 * repository'nin içine bakarak doğruluyoruz.
 */
class CatalogServiceTest {

    private static final Sku SKU = new Sku("SKU-1");
    private static final Price PRICE = new Price(new BigDecimal("100"), "TRY");

    private final InMemoryCatalogRepository repository = new InMemoryCatalogRepository();
    private final InMemoryProcessedUpdates processed = new InMemoryProcessedUpdates();
    private final CatalogService service = new CatalogService(repository, processed, new Transaction() {
        @Override
        public <T> T execute(Supplier<T> work) {
            return work.get();
        }
    });

    private static String newId() {
        return UUID.randomUUID().toString();
    }

    @Test
    void should_show_new_product_as_visible_and_out_of_stock() {
        service.apply(newId(), new CatalogUpdate.NewProduct(SKU, "Kupa", "Seramik kupa", PRICE, 1));

        assertThat(repository.items.get(SKU)).satisfies(item -> {
            assertThat(item.active()).isTrue();
            assertThat(item.outOfStock()).isTrue();
        });
    }

    @Test
    void should_not_duplicate_product_when_registered_twice() { // US1
        service.apply(newId(), new CatalogUpdate.NewProduct(SKU, "Kupa", "Seramik kupa", PRICE, 1));
        service.apply(newId(), new CatalogUpdate.NewProduct(SKU, "Kupa", "Seramik kupa", PRICE, 1));

        assertThat(repository.items).hasSize(1);
    }

    @Test
    void should_apply_stock_and_details_to_existing_product() {
        service.apply(newId(), new CatalogUpdate.NewProduct(SKU, "Kupa", "Seramik kupa", PRICE, 1));

        service.apply(newId(), new CatalogUpdate.StockChanged(SKU, 8, 1));
        service.apply(newId(), new CatalogUpdate.DetailsChanged(SKU, "Büyük kupa", null,
                new Price(new BigDecimal("120"), "TRY"), 2));

        assertThat(repository.items.get(SKU)).satisfies(item -> {
            assertThat(item.stock()).isEqualTo(8);
            assertThat(item.name()).isEqualTo("Büyük kupa");
        });
    }

    @Test
    void should_hide_withdrawn_product() {
        service.apply(newId(), new CatalogUpdate.NewProduct(SKU, "Kupa", "Seramik kupa", PRICE, 1));

        service.apply(newId(), new CatalogUpdate.Withdrawn(SKU, 2));

        assertThat(repository.items.get(SKU).active()).isFalse();
    }

    @Test
    void should_fail_for_unknown_product_so_that_it_can_be_retried() { // US5-2
        // Ürünün kendisi henüz gelmemiş olabilir (iki kuyruk arasında sıra garantisi yok).
        assertThatThrownBy(() -> service.apply(newId(), new CatalogUpdate.StockChanged(new Sku("SKU-9"), 4, 1)))
                .isInstanceOf(UnknownProductException.class)
                .hasMessageContaining("SKU-9");
    }

    @Test
    void should_apply_same_update_only_once() {
        service.apply(newId(), new CatalogUpdate.NewProduct(SKU, "Kupa", "Seramik kupa", PRICE, 1));
        String id = newId();

        service.apply(id, new CatalogUpdate.StockChanged(SKU, 8, 1));
        service.apply(id, new CatalogUpdate.StockChanged(SKU, 99, 2)); // aynı kimlik, farklı içerik

        assertThat(repository.items.get(SKU).stock()).isEqualTo(8);
    }

    @Test
    void should_require_update_id() {
        assertThatThrownBy(() -> service.apply(null, new CatalogUpdate.Withdrawn(SKU, 2)))
                .isInstanceOf(com.rabbitlab.storefront.domain.InvalidValueException.class);
    }
}
