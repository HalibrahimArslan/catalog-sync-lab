package com.rabbitlab.backoffice.adapter.out.outbox;

import com.rabbitlab.backoffice.application.InventoryService;
import com.rabbitlab.backoffice.application.ProductService;
import com.rabbitlab.backoffice.TestcontainersConfiguration;
import com.rabbitlab.backoffice.application.CountStockCommand;
import com.rabbitlab.backoffice.application.CreateProductCommand;
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

import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P2-M3 — Outbox relay: outbox tablosundaki mesajları RabbitMQ'ya taşır.
 *
 * <pre>
 *  [ product | inventory | outbox ]  ──relay──▶  backoffice.events (topic)  ──▶  kuyruklar
 *        tek transaction                 confirm + mandatory
 * </pre>
 *
 * Zamanlayıcı bu testte kapalı; relay'i elle çağırıyoruz ki ne olduğunu adım adım görelim.
 * Her test outbox'ı boşaltıp kendi kuyruğunu kurar, sonunda siler.
 */
@SpringBootTest(properties = {
        "backoffice.outbox.scheduling-enabled=false",
        "backoffice.outbox.batch-size=10"})
@Import(TestcontainersConfiguration.class)
class OutboxRelayIT {

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

    private final Price price = new Price(new BigDecimal("100"), "TRY");
    private final List<Queue> queues = new ArrayList<>();

    @BeforeEach
    void emptyOutbox() {
        jdbc.sql("delete from outbox").update();
    }

    @AfterEach
    void deleteQueues() {
        queues.forEach(queue -> admin.deleteQueue(queue.getName()));
    }

    @Test
    void should_publish_pending_messages_and_mark_them_published() {
        Queue queue = listenTo("product.*", "stock.*");
        Sku sku = newSku();
        productService.create(new CreateProductCommand(sku, "Kupa", "Seramik kupa", price));
        inventoryService.count(new CountStockCommand(sku, 5));

        int published = relay.relayPending();

        assertThat(published).isEqualTo(2);
        assertThat(unpublishedCount()).isZero();
        Message created = rabbit.receive(queue.getName(), 3_000);
        assertThat(created).isNotNull();
        assertThat(created.getMessageProperties().getType()).isEqualTo("ProductCreated");
        assertThat(created.getMessageProperties().getReceivedRoutingKey()).isEqualTo("product.created");
        assertThat(created.getMessageProperties().getContentType()).isEqualTo("application/json");
        assertThat(created.getMessageProperties().getMessageId()).isEqualTo(messageIdOf(sku, "ProductCreated"));
        // Gövde bir JSON nesnesi; jsonb anahtar sırasını ve boşlukları normalleştirir, bu yüzden metin
        // değil alan karşılaştırıyoruz.
        assertThat(json.readTree(created.getBody()).get("sku").asString()).isEqualTo(sku.value());
        assertThat(rabbit.receive(queue.getName(), 3_000).getMessageProperties().getType()).isEqualTo("StockUpdated");
    }

    @Test
    void should_not_publish_same_message_twice() {
        Queue queue = listenTo("product.*");
        productService.create(new CreateProductCommand(newSku(), "Kupa", "Seramik kupa", price));

        relay.relayPending();
        int secondRound = relay.relayPending();

        assertThat(secondRound).isZero();
        assertThat(admin.getQueueInfo(queue.getName()).getMessageCount()).isEqualTo(1);
    }

    @Test
    void should_keep_message_in_outbox_when_no_queue_listens() {
        // Mandatory: hiçbir kuyruğa gitmeyen mesaj "gönderildi" sayılmaz (Faz 1'de exception'dı).
        // Outbox'ta kalır; dinleyici gelince bir sonraki turda gider. Hiçbir mesaj kaybolmaz.
        Sku sku = newSku();
        productService.create(new CreateProductCommand(sku, "Kupa", "Seramik kupa", price));

        assertThat(relay.relayPending()).isZero();
        assertThat(outboxRow(sku)).satisfies(row -> {
            assertThat(row.get("published_at")).isNull();
            assertThat(row.get("attempts")).isEqualTo(1);
            assertThat(row.get("last_error").toString()).contains("NO_ROUTE");
        });

        Queue queue = listenTo("product.*");
        assertThat(relay.relayPending()).isEqualTo(1);
        assertThat(rabbit.receive(queue.getName(), 3_000)).isNotNull();
    }

    @Test
    void should_publish_messages_behind_a_failing_one() {
        // Ortadaki mesaj gidemiyor. Arkasındakiler onu beklemez: sıra garantisine ihtiyacımız yok,
        // çünkü Storefront versiyonla geç gelen mesajı zaten zararsız kılıyor.
        Queue queue = listenTo("product.*");
        productService.create(new CreateProductCommand(newSku(), "Kupa", "Seramik kupa", price));
        insertRaw("unknown.event");
        productService.create(new CreateProductCommand(newSku(), "Tabak", "Seramik tabak", price));

        assertThat(relay.relayPending()).isEqualTo(2);
        assertThat(unpublishedCount()).isEqualTo(1);
        assertThat(admin.getQueueInfo(queue.getName()).getMessageCount()).isEqualTo(2);
    }

    @Test
    void should_publish_each_message_once_when_two_relays_run_together() {
        // İki Backoffice instance'ı aynı outbox'ı işliyor. FOR UPDATE SKIP LOCKED: biri bir satırı
        // kilitlediyse diğeri onu atlar ve sonrakine geçer.
        Queue queue = listenTo("product.*");
        for (int i = 0; i < 40; i++) {
            productService.create(new CreateProductCommand(newSku(), "Ürün " + i, null, price));
        }

        CompletableFuture<Void> first = CompletableFuture.runAsync(this::drain);
        CompletableFuture<Void> second = CompletableFuture.runAsync(this::drain);
        CompletableFuture.allOf(first, second).join();
        drain(); // biri erken bıraktıysa kalanları topla

        assertThat(unpublishedCount()).isZero();
        assertThat(admin.getQueueInfo(queue.getName()).getMessageCount()).isEqualTo(40);
    }

    private void drain() {
        while (relay.relayPending() > 0) {
            // batch-size=10: 40 mesaj birkaç turda biter
        }
    }

    private Queue listenTo(String... bindingKeys) {
        // AnonymousQueue değil: o auto-delete; ilk receive() consumer'ı kapatınca kuyruk silinir.
        Queue queue = new Queue("test." + UUID.randomUUID(), true, false, false);
        admin.declareQueue(queue);
        for (String key : bindingKeys) {
            admin.declareBinding(BindingBuilder.bind(queue).to(new TopicExchange(BackofficeEvents.EXCHANGE)).with(key));
        }
        queues.add(queue);
        return queue;
    }

    private void insertRaw(String routingKey) {
        jdbc.sql("""
                        insert into outbox (message_id, sku, message_type, routing_key, payload)
                        values (?, 'SKU-X', 'Unknown', ?, '{}'::jsonb)""")
                .params(UUID.randomUUID(), routingKey)
                .update();
    }

    private static Sku newSku() {
        return new Sku("SKU-" + UUID.randomUUID().toString().substring(0, 8));
    }

    private int unpublishedCount() {
        return jdbc.sql("select count(*) from outbox where published_at is null").query(Integer.class).single();
    }

    private Map<String, Object> outboxRow(Sku sku) {
        return jdbc.sql("select * from outbox where sku = ?").param(sku.value()).query().singleRow();
    }

    private String messageIdOf(Sku sku, String type) {
        return jdbc.sql("select message_id::text from outbox where sku = ? and message_type = ?")
                .params(sku.value(), type).query(String.class).single();
    }
}
