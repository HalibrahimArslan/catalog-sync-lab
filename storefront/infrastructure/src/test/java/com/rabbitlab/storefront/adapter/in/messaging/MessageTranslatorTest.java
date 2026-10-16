package com.rabbitlab.storefront.adapter.in.messaging;

import com.rabbitlab.contract.BackofficeEvents;
import com.rabbitlab.storefront.application.CatalogUpdate;
import com.rabbitlab.storefront.domain.Price;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageBuilderSupport;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P4-M3 — Storefront iki sözleşme sürümünü de okur. Kırıcı değişikliğin güvenli sırasının ilk adımı:
 * üretici (Backoffice) v2'ye geçmeden önce tüketici iki sürümü de anlamalı.
 *
 * <ul>
 *   <li>Başlıksız mesaj v1: P4-M3 öncesi yayınlanmış, kuyrukta ya da park kuyruğunda bekleyen mesajlar.</li>
 *   <li>Bilinmeyen sürüm bozuk mesaj sayılır, hemen park edilir. Üretici tüketiciden önce yeni bir sürüme
 *       geçtiyse mesajlar kaybolmaz: Storefront güncellenince park kuyruğundan yeniden oynatılır (P4-M2).</li>
 * </ul>
 */
class MessageTranslatorTest {

    private static final String V1_CREATED = """
            {"sku":"SKU-1","name":"Kupa","description":"Seramik kupa","price":100.00,"currency":"TRY","version":1}""";
    private static final String V2_CREATED = """
            {"sku":"SKU-1","name":"Kupa","description":"Seramik kupa",
             "price":{"amount":100.00,"currency":"TRY"},"version":1}""";

    private static final Price PRICE = new Price(new BigDecimal("100"), "TRY");

    private final MessageTranslator translator = new MessageTranslator(JsonMapper.builder().build());

    @Test
    void should_read_message_without_version_header_as_v1() {
        CatalogUpdate update = translator.translate(message("ProductCreated", null, V1_CREATED));

        assertThat(update).isInstanceOfSatisfying(CatalogUpdate.NewProduct.class,
                created -> assertThat(created.price()).isEqualTo(PRICE));
    }

    @Test
    void should_read_v1() {
        assertThat(translator.translate(message("ProductCreated", 1, V1_CREATED)))
                .isInstanceOfSatisfying(CatalogUpdate.NewProduct.class,
                        created -> assertThat(created.price()).isEqualTo(PRICE));
    }

    @Test
    void should_read_v2_price_object() {
        assertThat(translator.translate(message("ProductCreated", 2, V2_CREATED)))
                .isInstanceOfSatisfying(CatalogUpdate.NewProduct.class, created -> {
                    assertThat(created.sku().value()).isEqualTo("SKU-1");
                    assertThat(created.name()).isEqualTo("Kupa");
                    assertThat(created.price()).isEqualTo(PRICE);
                });
    }

    @Test
    void should_read_v2_product_update() {
        String body = """
                {"sku":"SKU-1","name":"Kupa","description":null,"price":{"amount":120,"currency":"TRY"},"version":2}""";

        assertThat(translator.translate(message("ProductUpdated", 2, body)))
                .isInstanceOfSatisfying(CatalogUpdate.DetailsChanged.class, changed -> {
                    assertThat(changed.price().amount()).isEqualByComparingTo("120");
                    assertThat(changed.version()).isEqualTo(2);
                });
    }

    @Test
    void should_read_stock_update_in_both_versions() { // fiyat taşımayan mesajın gövdesi iki sürümde aynı
        String body = "{\"sku\":\"SKU-1\",\"quantity\":8,\"version\":3}";

        assertThat(translator.translate(message("StockUpdated", 1, body))).isEqualTo(
                translator.translate(message("StockUpdated", 2, body)));
    }

    @Test
    void should_reject_v1_body_labelled_as_v2() {
        assertThatThrownBy(() -> translator.translate(message("ProductCreated", 2, V1_CREATED)))
                .isInstanceOf(InvalidMessageException.class);
    }

    @Test
    void should_reject_v2_message_without_price() { // NullPointerException değil: geçici hata sanılıp 3 kez denenirdi
        String body = "{\"sku\":\"SKU-1\",\"name\":\"Kupa\",\"version\":1}";

        assertThatThrownBy(() -> translator.translate(message("ProductCreated", 2, body)))
                .isInstanceOf(InvalidMessageException.class);
    }

    @Test
    void should_reject_unknown_schema_version() {
        assertThatThrownBy(() -> translator.translate(message("ProductCreated", 3, V2_CREATED)))
                .isInstanceOf(InvalidMessageException.class)
                .hasMessageContaining("3");
    }

    @Test
    void should_reject_unreadable_schema_version() {
        Message message = MessageBuilder.withBody(V1_CREATED.getBytes(UTF_8)).setType("ProductCreated")
                .setHeader(BackofficeEvents.SCHEMA_VERSION_HEADER, "iki").build();

        assertThatThrownBy(() -> translator.translate(message))
                .isInstanceOf(InvalidMessageException.class)
                .hasMessageContaining("iki");
    }

    private static Message message(String type, Integer schemaVersion, String body) {
        MessageBuilderSupport<Message> builder = MessageBuilder.withBody(body.getBytes(UTF_8)).setType(type)
                .setContentType("application/json");
        if (schemaVersion != null) {
            builder.setHeader(BackofficeEvents.SCHEMA_VERSION_HEADER, schemaVersion);
        }
        return builder.build();
    }
}
