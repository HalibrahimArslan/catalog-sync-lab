package com.rabbitlab.storefront.adapter.in.messaging;

import com.rabbitlab.contract.BackofficeEvents;
import com.rabbitlab.contract.BackofficeMessage;
import com.rabbitlab.contract.ProductCreatedMessage;
import com.rabbitlab.contract.ProductDeactivatedMessage;
import com.rabbitlab.contract.ProductUpdatedMessage;
import com.rabbitlab.contract.StockUpdatedMessage;
import com.rabbitlab.storefront.TestcontainersConfiguration;
import com.rabbitlab.storefront.domainservice.CatalogRepository;
import com.rabbitlab.storefront.domain.CatalogItem;
import com.rabbitlab.storefront.domain.Sku;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * P2-M5 — Storefront'un RabbitMQ girişi (inbound adapter).
 *
 * <pre>
 *  backoffice.events ─ product.* ─▶ storefront.catalog ⇄ storefront.catalog.retry   (TTL)
 *                    │                     └─▶ storefront.catalog.dlq               (park)
 *                    └ stock.*   ─▶ storefront.stock   ⇄ storefront.stock.retry
 *                                          └─▶ storefront.stock.dlq
 * </pre>
 *
 * Faz 1 M5 ile aynı davranış; fark nerede durduğunda. Hata politikası (retry, park, x-death) tamamen
 * adapter'da; use case ve domain RabbitMQ'yu bilmiyor (acıtan nokta 1). Testler Backoffice yerine
 * sözleşmeye uygun mesajları doğrudan exchange'e gönderiyor.
 */
@SpringBootTest(properties = "storefront.messaging.retry-delay=200ms")
@Import(TestcontainersConfiguration.class)
class CatalogMessagingIT {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    RabbitTemplate rabbit;

    @Autowired
    JsonMapper json;

    /** Kataloğun iç durumuna (versiyonlar dahil) bakmak için doğrudan repository. */
    @Autowired
    CatalogRepository catalog;

    private final String sku = "SKU-" + UUID.randomUUID().toString().substring(0, 8);

    private ProductCreatedMessage created() {
        return new ProductCreatedMessage(sku, "Kupa", "Seramik kupa", new BigDecimal("100.00"), "TRY", 1);
    }

    @Test
    void should_sync_catalog_from_backoffice_messages() { // US1–US4
        publish(created());
        publish(new StockUpdatedMessage(sku, 8, 1));
        publish(new ProductUpdatedMessage(sku, "Kupa", "Seramik kupa", new BigDecimal("120.00"), "TRY", 2));

        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(item()).satisfies(item -> {
            assertThat(item.stock()).isEqualTo(8);
            assertThat(item.price().amount()).isEqualByComparingTo("120");
        }));

        publish(new ProductDeactivatedMessage(sku, 3));
        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(item().active()).isFalse());
    }

    @Test
    void should_apply_stock_that_arrived_before_its_product() { // iki kuyruk arasında sıra garantisi yok
        publish(new StockUpdatedMessage(sku, 6, 1));
        await().pollDelay(Duration.ofMillis(300)).until(() -> true); // ilk deneme başarısız olsun

        publish(created());

        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(item().stock()).isEqualTo(6));
        assertThat(parkedFor("storefront.stock.dlq")).isNull();
    }

    @Test
    void should_park_stock_after_three_attempts_when_product_never_arrives() { // US5-2
        publish(new StockUpdatedMessage(sku, 4, 1));

        Message parked = awaitParked("storefront.stock.dlq");

        assertThat(parked.getMessageProperties().getType()).isEqualTo("StockUpdated");
        assertThat(parked.getMessageProperties().<String>getHeader("x-error")).contains("bilinmeyen ürün", sku);
        assertThat(parked.getMessageProperties().<String>getHeader("x-original-queue")).isEqualTo("storefront.stock");
        assertThat(parked.getMessageProperties().<Long>getHeader("x-attempts")).isEqualTo(3L);
    }

    @Test
    void should_park_invalid_message_immediately_and_keep_processing_others() { // US5-3
        publish(created());
        await().atMost(TIMEOUT).until(() -> catalog.find(new Sku(sku)).isPresent());

        publishRaw("StockUpdated", "stock.updated", "{\"sku\":\"" + sku + "\",\"quantity\":-5,\"version\":1}");
        publish(new StockUpdatedMessage(sku, 3, 2));

        Message parked = awaitParked("storefront.stock.dlq");
        assertThat(new String(parked.getBody(), UTF_8)).contains("-5");
        assertThat(parked.getMessageProperties().<Long>getHeader("x-attempts")).isEqualTo(1L); // tekrar denenmedi
        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(item().stock()).isEqualTo(3));
    }

    @Test
    void should_park_message_of_unknown_type() {
        publishRaw("PriceChanged", "product.price-changed", "{\"sku\":\"" + sku + "\"}");

        Message parked = awaitParked("storefront.catalog.dlq");
        assertThat(parked.getMessageProperties().<String>getHeader("x-error")).contains("PriceChanged");
    }

    @Test
    void should_ignore_redelivered_message_with_same_id() {
        // Relay, onay aldıktan sonra published_at yazamadan çökerse aynı mesajı AYNI message-id ile tekrar
        // gönderir. Inbox: işlenmiş message-id ikinci kez işlenmez. Farkı görmek için ikinci gönderimin
        // içeriğini (ve versiyonunu) değiştiriyoruz; versiyon kontrolü tek başına bunu uygulardı.
        String marker = sku + "-M";
        publish(created());
        publish(new ProductCreatedMessage(marker, "İşaret", null, new BigDecimal("1.00"), "TRY", 1));
        await().atMost(TIMEOUT).until(() ->
                catalog.find(new Sku(sku)).isPresent() && catalog.find(new Sku(marker)).isPresent());
        String messageId = UUID.randomUUID().toString();

        publish(new StockUpdatedMessage(sku, 8, 1), messageId);
        publish(new StockUpdatedMessage(sku, 99, 2), messageId);
        publish(new StockUpdatedMessage(marker, 1, 1));

        // Aynı kuyruk, tek consumer: işaret işlendiyse önceki iki mesaj da işlendi.
        await().atMost(TIMEOUT).until(() -> catalog.find(new Sku(marker)).orElseThrow().stock() == 1);
        assertThat(item().stock()).isEqualTo(8);
        assertThat(processedCount(messageId)).isEqualTo(1);
    }

    @Test
    void should_park_message_without_id() {
        // Inbox kimliksiz mesajı ayıklayamaz. Backoffice her mesaja kimlik koyar; kimliksiz mesaj bozuktur.
        rabbit.send(BackofficeEvents.EXCHANGE, "product.created", MessageBuilder
                .withBody(json.writeValueAsBytes(created()))
                .setContentType("application/json")
                .setType("ProductCreated")
                .build());

        Message parked = awaitParked("storefront.catalog.dlq");
        assertThat(parked.getMessageProperties().<String>getHeader("x-error")).contains("message-id");
        assertThat(catalog.find(new Sku(sku))).isEmpty();
    }

    @Test
    void should_record_processed_message_ids() {
        String messageId = UUID.randomUUID().toString();

        publish(created(), messageId);

        await().atMost(TIMEOUT).until(() -> processedCount(messageId) == 1);
    }

    // --- yardımcılar ---

    @Autowired
    org.springframework.jdbc.core.simple.JdbcClient jdbc;

    private int processedCount(String messageId) {
        return jdbc.sql("select count(*) from processed_message where message_id = ?").param(messageId)
                .query(Integer.class).single();
    }

    private CatalogItem item() {
        return catalog.find(new Sku(sku)).orElseThrow();
    }

    private void publish(BackofficeMessage message) {
        publish(message, UUID.randomUUID().toString());
    }

    private void publish(BackofficeMessage message, String messageId) {
        rabbit.send(BackofficeEvents.EXCHANGE, BackofficeEvents.routingKeyOf(message), MessageBuilder
                .withBody(json.writeValueAsBytes(message))
                .setContentType("application/json")
                .setType(BackofficeEvents.typeOf(message))
                .setMessageId(messageId)
                .build());
    }

    private void publishRaw(String type, String routingKey, String body) {
        rabbit.send(BackofficeEvents.EXCHANGE, routingKey, MessageBuilder.withBody(body.getBytes(UTF_8))
                .setContentType("application/json").setType(type).setMessageId(UUID.randomUUID().toString()).build());
    }

    /** Park kuyruğu testler arasında ortak; bu testin SKU'sunu taşıyan mesajı arar. */
    private Message awaitParked(String dlq) {
        AtomicReference<Message> found = new AtomicReference<>();
        await().atMost(TIMEOUT).until(() -> {
            found.set(parkedFor(dlq));
            return found.get() != null;
        });
        return found.get();
    }

    private Message parkedFor(String dlq) {
        Message message;
        while ((message = rabbit.receive(dlq)) != null) {
            if (new String(message.getBody(), UTF_8).contains(sku)) {
                return message;
            }
        }
        return null;
    }
}
