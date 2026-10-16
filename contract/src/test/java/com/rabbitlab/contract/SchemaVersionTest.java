package com.rabbitlab.contract;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P4-M3 — Sözleşme sürümü (acıtan nokta 7). Kırıcı değişiklik: v1'de fiyat iki düz alan
 * ({@code price}, {@code currency}), v2'de tek nesne ({@code price: {amount, currency}}).
 *
 * <p>Sürüm AMQP başlığında ({@code x-schema-version}); tip adı ve routing key sürümle değişmez.
 * Fiyat taşımayan mesajlar (stok, pasifleştirme) iki sürümde aynı record.
 */
class SchemaVersionTest {

    private static final Money PRICE = new Money(new BigDecimal("100.00"), "TRY");

    @Test
    void should_carry_schema_version_in_a_header() {
        assertThat(BackofficeEvents.SCHEMA_VERSION_HEADER).isEqualTo("x-schema-version");
        assertThat(BackofficeEvents.isSupported(1)).isTrue();
        assertThat(BackofficeEvents.isSupported(2)).isTrue();
        assertThat(BackofficeEvents.isSupported(3)).isFalse();
    }

    @Test
    void should_resolve_message_class_per_schema_version() {
        assertThat(BackofficeEvents.classOf("ProductCreated", 1)).isEqualTo(ProductCreatedMessage.class);
        assertThat(BackofficeEvents.classOf("ProductCreated", 2)).isEqualTo(ProductCreatedMessageV2.class);
        assertThat(BackofficeEvents.classOf("ProductUpdated", 2)).isEqualTo(ProductUpdatedMessageV2.class);
        assertThat(BackofficeEvents.classOf("StockUpdated", 2)).isEqualTo(StockUpdatedMessage.class);
        assertThat(BackofficeEvents.classOf("ProductDeactivated", 2)).isEqualTo(ProductDeactivatedMessage.class);
    }

    @Test
    void should_treat_versionless_lookup_as_v1() { // P4 öncesi mesajların başlığı yok
        assertThat(BackofficeEvents.classOf("ProductCreated")).isEqualTo(ProductCreatedMessage.class);
    }

    @Test
    void should_keep_type_names_and_routing_keys_across_versions() {
        ProductCreatedMessageV2 created = new ProductCreatedMessageV2("SKU-1", "Kupa", null, PRICE, 1);
        ProductUpdatedMessageV2 updated = new ProductUpdatedMessageV2("SKU-1", "Kupa", null, PRICE, 2);

        assertThat(BackofficeEvents.typeOf(created)).isEqualTo("ProductCreated");
        assertThat(BackofficeEvents.routingKeyOf(created)).isEqualTo("product.created");
        assertThat(BackofficeEvents.typeOf(updated)).isEqualTo("ProductUpdated");
        assertThat(BackofficeEvents.routingKeyOf(updated)).isEqualTo("product.updated");
    }

    @Test
    void should_reject_unknown_schema_version() {
        assertThatThrownBy(() -> BackofficeEvents.classOf("ProductCreated", 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("3");
    }
}
