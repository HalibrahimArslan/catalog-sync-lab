package com.rabbitlab.backoffice.domainservice;

import com.rabbitlab.backoffice.domain.RuleViolationException;
import com.rabbitlab.backoffice.domain.Sku;
import com.rabbitlab.backoffice.domain.event.ProductCreated;
import com.rabbitlab.backoffice.domain.product.Price;
import com.rabbitlab.backoffice.domain.product.Product;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P3-M1 — Domain servisi: tek bir aggregate'in cevaplayamadığı iş kuralı.
 *
 * <p>"SKU benzersizdir" kuralını {@code Product} kendi başına bilemez; diğer ürünleri görmesi gerekir.
 * "Ürün stoğuyla birlikte açılır" kuralı da iki aggregate'e yayılır. Faz 2'de ikisi de application
 * servisinin içindeydi; kural mıydı, akış mıydı belli değildi. Onion'da ayrı bir halka: domain servisleri.
 *
 * <p>Domain servisi kaydetmez: yeni aggregate'leri üretir, kaydetmek ve event'leri outbox'a yazmak
 * application servisinin işi.
 */
class ProductRegistrationTest {

    private static final Sku SKU = new Sku("SKU-1");
    private static final Price PRICE = new Price(new BigDecimal("100"), "TRY");

    private final InMemoryProductRepository products = new InMemoryProductRepository();
    private final ProductRegistration registration = new ProductRegistration(products);

    @Test
    void should_open_product_together_with_empty_inventory() {
        ProductRegistration.Registered registered = registration.register(SKU, "Kupa", "Seramik kupa", PRICE);

        assertThat(registered.product().sku()).isEqualTo(SKU);
        assertThat(registered.product().pullEvents())
                .containsExactly(new ProductCreated(SKU, "Kupa", "Seramik kupa", PRICE, 1));
        assertThat(registered.inventory().sku()).isEqualTo(SKU);
        assertThat(registered.inventory().quantity()).isZero();
    }

    @Test
    void should_not_save_anything_itself() {
        registration.register(SKU, "Kupa", "Seramik kupa", PRICE);

        assertThat(products.saved).isEmpty();
    }

    @Test
    void should_reject_sku_that_is_already_taken() {
        products.save(Product.create(SKU, "Kupa", "Seramik kupa", PRICE));

        assertThatThrownBy(() -> registration.register(SKU, "Başka", null, PRICE))
                .isInstanceOf(DuplicateSkuException.class)
                .isInstanceOf(RuleViolationException.class)
                .hasMessageContaining("SKU-1");
    }

    @Test
    void should_report_missing_product_through_repository_contract() {
        // Repository sözleşmesinin parçası: "getir, yoksa ProductNotFoundException".
        assertThatThrownBy(() -> products.get(SKU))
                .isInstanceOf(ProductNotFoundException.class)
                .isInstanceOf(com.rabbitlab.backoffice.domain.NotFoundException.class);
    }
}
