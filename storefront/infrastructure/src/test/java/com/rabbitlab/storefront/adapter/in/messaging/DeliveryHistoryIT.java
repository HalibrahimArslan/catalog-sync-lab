package com.rabbitlab.storefront.adapter.in.messaging;

import com.rabbitlab.contract.BackofficeEvents;
import com.rabbitlab.contract.BackofficeMessage;
import com.rabbitlab.contract.ProductCreatedMessage;
import com.rabbitlab.contract.StockUpdatedMessage;
import com.rabbitlab.storefront.TestcontainersConfiguration;
import com.rabbitlab.storefront.application.DeliveryEntry;
import com.rabbitlab.storefront.application.DeliveryLog;
import com.rabbitlab.storefront.application.DeliveryQuery;
import com.rabbitlab.storefront.application.DeliverySummary;
import com.rabbitlab.storefront.application.ParkedMessages;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static com.rabbitlab.storefront.application.DeliveryOutcome.DUPLICATE;
import static com.rabbitlab.storefront.application.DeliveryOutcome.PARKED;
import static com.rabbitlab.storefront.application.DeliveryOutcome.PROCESSED;
import static com.rabbitlab.storefront.application.DeliveryOutcome.REPLAYED;
import static com.rabbitlab.storefront.application.DeliveryOutcome.RETRY;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Mesaj takibi uçtan uca: listener her denemenin sonucunu ve hatasını teslimat geçmişine yazar.
 * Tekrar denenen mesajın neden başarısız olduğu artık görülebiliyor (önceden sadece log'daydı).
 */
@SpringBootTest(properties = "storefront.messaging.retry-delay=200ms")
@Import(TestcontainersConfiguration.class)
class DeliveryHistoryIT {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    DeliveryLog log;

    @Autowired
    ParkedMessages parked;

    @Autowired
    RabbitTemplate rabbit;

    @Autowired
    JsonMapper json;

    private final String sku = "SKU-" + UUID.randomUUID().toString().substring(0, 8);

    @Test
    void should_record_processed_message_with_what_it_was() {
        String id = publish(created());

        DeliverySummary summary = awaitState(id, "PROCESSED");

        assertThat(summary.sku()).isEqualTo(sku);
        assertThat(summary.type()).isEqualTo("ProductCreated");
        assertThat(summary.queue()).isEqualTo("storefront.catalog");
        assertThat(summary.schemaVersion()).as("başlıksız mesaj v1").isEqualTo(1);
        assertThat(summary.attempts()).isEqualTo(1);
    }

    @Test
    void should_record_duplicate_delivery() {
        String id = UUID.randomUUID().toString();
        publish(created(), id);
        publish(created(), id);

        await().atMost(TIMEOUT).untilAsserted(() ->
                assertThat(outcomes(id)).containsExactly(PROCESSED, DUPLICATE));
    }

    @Test
    void should_record_each_failed_attempt_with_its_error_until_parked() {
        String id = publish(new StockUpdatedMessage(sku, 4, 1));

        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(outcomes(id)).containsExactly(RETRY, RETRY, PARKED));
        assertThat(log.history(id)).extracting(DeliveryEntry::attempt).containsExactly(1, 2, 3);
        assertThat(log.history(id)).allSatisfy(entry -> assertThat(entry.error()).contains("bilinmeyen ürün", sku));
    }

    @Test
    void should_record_invalid_message_as_parked_on_first_attempt() {
        String id = UUID.randomUUID().toString();
        publishRaw("{\"sku\":\"" + sku + "\",\"quantity\":-5,\"version\":1}", id);

        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(log.history(id)).singleElement().satisfies(entry -> {
            assertThat(entry.outcome()).isEqualTo(PARKED);
            assertThat(entry.attempt()).isEqualTo(1);
            assertThat(entry.error()).contains("-5");
        }));
        assertThat(log.find(new DeliveryQuery(null, id, null, 1))).singleElement()
                .satisfies(summary -> assertThat(summary.sku()).as("bozuk mesajın da SKU'su gövdeden okunur").isEqualTo(sku));
    }

    @Test
    void should_record_recovery_after_transient_failure() { // iki kuyruk arası sıra garantisi yok
        String id = publish(new StockUpdatedMessage(sku, 6, 1));
        await().atMost(TIMEOUT).until(() -> outcomes(id).contains(RETRY));

        publish(created());

        await().atMost(TIMEOUT).untilAsserted(() -> {
            List<?> outcomes = outcomes(id);
            assertThat(outcomes.getFirst()).isEqualTo(RETRY);
            assertThat(outcomes.getLast()).isEqualTo(PROCESSED);
        });
    }

    @Test
    void should_record_replay_and_what_happened_after_it() {
        String id = publish(new StockUpdatedMessage(sku, 4, 1));
        await().atMost(TIMEOUT).until(() -> outcomes(id).contains(PARKED));
        publish(created());
        await().atMost(TIMEOUT).until(() -> !log.find(new DeliveryQuery(sku, null, PROCESSED, 10)).isEmpty());

        assertThat(parked.replay("storefront.stock", id)).isTrue();

        await().atMost(TIMEOUT).untilAsserted(() ->
                assertThat(outcomes(id)).containsExactly(RETRY, RETRY, PARKED, REPLAYED, PROCESSED));
    }

    @Test
    void should_record_message_without_id_under_its_sku() {
        rabbit.send(BackofficeEvents.EXCHANGE, "product.created", MessageBuilder
                .withBody(json.writeValueAsBytes(created()))
                .setContentType("application/json").setType("ProductCreated").build());

        await().atMost(TIMEOUT).untilAsserted(() ->
                assertThat(log.find(new DeliveryQuery(sku, null, null, 10))).singleElement().satisfies(summary -> {
                    assertThat(summary.messageId()).isNull();
                    assertThat(summary.state()).isEqualTo(PARKED);
                    assertThat(summary.lastError()).contains("message-id");
                }));
    }

    // --- yardımcılar ---

    private ProductCreatedMessage created() {
        return new ProductCreatedMessage(sku, "Kupa", null, new BigDecimal("100.00"), "TRY", 1);
    }

    private List<?> outcomes(String id) {
        return log.history(id).stream().map(DeliveryEntry::outcome).toList();
    }

    private DeliverySummary awaitState(String id, String state) {
        return await().atMost(TIMEOUT).until(() -> log.find(new DeliveryQuery(null, id, null, 1)).stream()
                .filter(s -> s.state().name().equals(state)).findFirst().orElse(null), s -> s != null);
    }

    private String publish(BackofficeMessage message) {
        return publish(message, UUID.randomUUID().toString());
    }

    private String publish(BackofficeMessage message, String id) {
        rabbit.send(BackofficeEvents.EXCHANGE, BackofficeEvents.routingKeyOf(message), MessageBuilder
                .withBody(json.writeValueAsBytes(message))
                .setContentType("application/json")
                .setType(BackofficeEvents.typeOf(message))
                .setMessageId(id)
                .build());
        return id;
    }

    private void publishRaw(String body, String id) {
        rabbit.send(BackofficeEvents.EXCHANGE, "stock.updated", MessageBuilder.withBody(body.getBytes(UTF_8))
                .setContentType("application/json").setType("StockUpdated").setMessageId(id).build());
    }
}
