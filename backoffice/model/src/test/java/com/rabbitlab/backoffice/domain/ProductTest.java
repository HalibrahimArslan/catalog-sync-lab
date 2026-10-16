package com.rabbitlab.backoffice.domain;

import com.rabbitlab.backoffice.domain.event.ProductCreated;
import com.rabbitlab.backoffice.domain.event.ProductDeactivated;
import com.rabbitlab.backoffice.domain.event.ProductUpdated;
import com.rabbitlab.backoffice.domain.product.Price;
import com.rabbitlab.backoffice.domain.product.Product;
import com.rabbitlab.backoffice.domain.product.ProductInactiveException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P2-M1 — Product aggregate'i. Faz 1'de event'leri testler elle oluşturuyordu ve versiyonu kimse
 * yönetmiyordu (acıtan nokta 3). Artık kurallar ve versiyon artışı aggregate'in içinde.
 * Saf unit test: Spring yok, veritabanı yok, RabbitMQ yok.
 */
class ProductTest {

    private static final Sku SKU = new Sku("SKU-1");

    private static Price try_(String amount) {
        return new Price(new BigDecimal(amount), "TRY");
    }

    private static Product newProduct() {
        return Product.create(SKU, "Kupa", "Seramik kupa", try_("100"));
    }

    @Test
    void should_start_active_at_version_1_and_record_created_event() {
        Product product = newProduct();

        assertThat(product.active()).isTrue();
        assertThat(product.version()).isEqualTo(1);
        assertThat(product.pullEvents())
                .containsExactly(new ProductCreated(SKU, "Kupa", "Seramik kupa", try_("100"), 1));
    }

    @Test
    void should_hand_out_events_only_once() {
        Product product = newProduct();

        product.pullEvents();

        assertThat(product.pullEvents()).isEmpty();
    }

    @Test
    void should_increase_version_and_record_event_when_details_change() {
        Product product = newProduct();
        product.pullEvents();

        product.update("Büyük kupa", "Seramik kupa", try_("120"));

        assertThat(product.version()).isEqualTo(2);
        assertThat(product.price()).isEqualTo(try_("120"));
        assertThat(product.pullEvents())
                .containsExactly(new ProductUpdated(SKU, "Büyük kupa", "Seramik kupa", try_("120"), 2));
    }

    @Test
    void should_not_change_version_when_update_changes_nothing() {
        Product product = newProduct();
        product.pullEvents();

        product.update("Kupa", "Seramik kupa", try_("100.00")); // 100 ile 100.00 aynı fiyat

        assertThat(product.version()).isEqualTo(1);
        assertThat(product.pullEvents()).isEmpty();
    }

    @Test
    void should_hide_product_with_new_version_when_deactivated() {
        Product product = newProduct();
        product.pullEvents();

        product.deactivate();

        assertThat(product.active()).isFalse();
        assertThat(product.pullEvents()).containsExactly(new ProductDeactivated(SKU, 2));
    }

    @Test
    void should_ignore_second_deactivation() {
        Product product = newProduct();
        product.deactivate();
        product.pullEvents();

        product.deactivate();

        assertThat(product.version()).isEqualTo(2);
        assertThat(product.pullEvents()).isEmpty();
    }

    @Test
    void should_reject_update_of_inactive_product() {
        // Pasif ürünü tekrar aktifleştirmek kapsam dışı; pasif ürünü güncellemek de anlamsız.
        Product product = newProduct();
        product.deactivate();

        assertThatThrownBy(() -> product.update("Kupa", "Seramik kupa", try_("90")))
                .isInstanceOf(ProductInactiveException.class)
                .isInstanceOf(RuleViolationException.class) // REST bunu 409'a çevirir
                .hasMessageContaining("SKU-1");
    }

    @Test
    void should_require_name() {
        assertThatThrownBy(() -> Product.create(SKU, " ", "Seramik kupa", try_("100")))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    void should_require_positive_price() {
        assertThatThrownBy(() -> try_("0")).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> try_("-1")).isInstanceOf(InvalidValueException.class);
    }

    @Test
    void should_require_sku() {
        assertThatThrownBy(() -> new Sku("")).isInstanceOf(InvalidValueException.class);
    }
}
