package com.rabbitlab.backoffice.adapter.out.outbox;

import com.rabbitlab.backoffice.TestcontainersConfiguration;
import com.rabbitlab.backoffice.application.CountStockCommand;
import com.rabbitlab.backoffice.application.CreateProductCommand;
import com.rabbitlab.backoffice.application.InventoryService;
import com.rabbitlab.backoffice.application.ProductService;
import com.rabbitlab.backoffice.domain.Sku;
import com.rabbitlab.backoffice.domain.product.Price;
import com.rabbitlab.contract.BackofficeEvents;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P4-M3 — Backoffice yeni sözleşme sürümüyle (v2) yayınlıyor. Sürüm ayarla seçilir; ama her outbox
 * satırı <b>yazıldığı andaki</b> sürümü taşır. Ayar değiştiğinde outbox'ta bekleyen eski satırlar
 * yanlış etiketle gitmez: relay sürümü ayardan değil satırdan okur.
 */
@SpringBootTest(properties = {
        "backoffice.outbox.scheduling-enabled=false",
        "backoffice.outbox.schema-version=2"})
@Import(TestcontainersConfiguration.class)
class OutboxSchemaVersionIT {

    @Autowired
    OutboxRelay relay;

    @Autowired
    ProductService productService;

    @Autowired
    InventoryService inventoryService;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    AmqpAdmin admin;

    @Autowired
    RabbitTemplate rabbit;

    @Autowired
    JsonMapper json;

    private final Sku sku = new Sku("SKU-" + UUID.randomUUID().toString().substring(0, 8));
    private Queue queue;

    @BeforeEach
    void listen() {
        jdbc.sql("delete from outbox").update();
        queue = new Queue("test." + UUID.randomUUID(), true, false, false);
        admin.declareQueue(queue);
        TopicExchange exchange = new TopicExchange(BackofficeEvents.EXCHANGE);
        admin.declareBinding(BindingBuilder.bind(queue).to(exchange).with("product.*"));
        admin.declareBinding(BindingBuilder.bind(queue).to(exchange).with("stock.*"));
    }

    @AfterEach
    void deleteQueue() {
        admin.deleteQueue(queue.getName());
    }

    @Test
    void should_write_v2_payload_and_its_version_into_outbox() {
        productService.create(new CreateProductCommand(sku, "Kupa", "Seramik kupa",
                new Price(new BigDecimal("100"), "TRY")));

        assertThat(rowVersion("ProductCreated")).isEqualTo(2);
        JsonNode payload = json.readTree(jdbc.sql("select payload::text from outbox where sku = ?")
                .param(sku.value()).query(String.class).single());
        assertThat(payload.get("price").get("amount").decimalValue()).isEqualByComparingTo("100");
        assertThat(payload.get("price").get("currency").asString()).isEqualTo("TRY");
        assertThat(payload.has("currency")).as("v2'de para birimi fiyat nesnesinin içinde").isFalse();
    }

    @Test
    void should_publish_schema_version_header_taken_from_outbox_row() {
        productService.create(new CreateProductCommand(sku, "Kupa", null, new Price(new BigDecimal("100"), "TRY")));
        inventoryService.count(new CountStockCommand(sku, 4));

        assertThat(relay.relayPending()).isEqualTo(2);

        assertThat(schemaVersionOf(receive())).isEqualTo(2); // ProductCreated
        assertThat(schemaVersionOf(receive())).isEqualTo(2); // StockUpdated: gövdesi v1 ile aynı, etiketi yine 2
    }

    @Test
    void should_publish_old_rows_with_their_own_version_after_setting_changed() {
        // Ayar 1'ken yazılmış, henüz gitmemiş bir satır (P4-M3 öncesi satırlar da böyle: sütun varsayılanı 1).
        jdbc.sql("""
                        insert into outbox (message_id, sku, message_type, routing_key, payload)
                        values (?, ?, 'StockUpdated', 'stock.updated', ?::jsonb)""")
                .params(UUID.randomUUID(), sku.value(), "{\"sku\":\"" + sku.value() + "\",\"quantity\":1,\"version\":1}")
                .update();

        assertThat(relay.relayPending()).isEqualTo(1);

        assertThat(schemaVersionOf(receive())).isEqualTo(1);
    }

    private Message receive() {
        Message message = rabbit.receive(queue.getName(), 3_000);
        assertThat(message).isNotNull();
        return message;
    }

    private static int schemaVersionOf(Message message) {
        Object header = message.getMessageProperties().getHeader(BackofficeEvents.SCHEMA_VERSION_HEADER);
        assertThat(header).isInstanceOf(Number.class);
        return ((Number) header).intValue();
    }

    private int rowVersion(String type) {
        return jdbc.sql("select schema_version from outbox where sku = ? and message_type = ?")
                .params(sku.value(), type).query(Integer.class).single();
    }
}
