package com.rabbitlab.storefront.adapter.out.persistence;

import com.rabbitlab.storefront.TestcontainersConfiguration;
import com.rabbitlab.storefront.application.DeliveryAttempt;
import com.rabbitlab.storefront.application.DeliveryEntry;
import com.rabbitlab.storefront.application.DeliveryLog;
import com.rabbitlab.storefront.application.DeliveryOutcome;
import com.rabbitlab.storefront.application.DeliveryQuery;
import com.rabbitlab.storefront.application.DeliverySummary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.rabbitlab.storefront.application.DeliveryOutcome.DUPLICATE;
import static com.rabbitlab.storefront.application.DeliveryOutcome.PARKED;
import static com.rabbitlab.storefront.application.DeliveryOutcome.PROCESSED;
import static com.rabbitlab.storefront.application.DeliveryOutcome.REPLAYED;
import static com.rabbitlab.storefront.application.DeliveryOutcome.RETRY;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teslimat geçmişi (mesaj takibi): her denemenin sonucu ve hatası. Celery'nin "result backend"inin
 * karşılığı. RabbitMQ işlenen mesajı unutur, tekrar denenen mesajın neden başarısız olduğunu da hiçbir
 * yere yazmaz; bu tablo yazar.
 *
 * <p>Bir mesajın durumu, son denemesinin sonucudur. Yeniden oynatma (REPLAYED) bir deneme sayılmaz.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class DeliveryLogIT {

    @Autowired
    DeliveryLog log;

    @Autowired
    JdbcDeliveryLog jdbcLog;

    @Autowired
    JdbcClient jdbc;

    private final String sku = "SKU-" + UUID.randomUUID().toString().substring(0, 8);

    private DeliveryAttempt attempt(String messageId, int attempt, DeliveryOutcome outcome, String error) {
        return new DeliveryAttempt(messageId, "storefront.stock", "StockUpdated", sku, 1, attempt, outcome, error);
    }

    private static String newId() {
        return UUID.randomUUID().toString();
    }

    private List<DeliverySummary> mine() {
        return log.find(new DeliveryQuery(sku, null, null, 100));
    }

    @Test
    void should_summarize_message_by_its_last_outcome() {
        String id = newId();
        log.record(attempt(id, 1, RETRY, "UnknownProductException: bilinmeyen ürün"));
        log.record(attempt(id, 2, RETRY, "UnknownProductException: bilinmeyen ürün"));
        log.record(attempt(id, 3, PARKED, "UnknownProductException: bilinmeyen ürün"));

        assertThat(mine()).singleElement().satisfies(summary -> {
            assertThat(summary.messageId()).isEqualTo(id);
            assertThat(summary.state()).isEqualTo(PARKED);
            assertThat(summary.attempts()).isEqualTo(3);
            assertThat(summary.lastError()).contains("bilinmeyen ürün");
            assertThat(summary.type()).isEqualTo("StockUpdated");
            assertThat(summary.queue()).isEqualTo("storefront.stock");
            assertThat(summary.schemaVersion()).isEqualTo(1);
            assertThat(summary.firstSeen()).isBeforeOrEqualTo(summary.lastSeen());
        });
    }

    @Test
    void should_not_count_replay_as_attempt_and_keep_last_error_after_recovery() {
        String id = newId();
        log.record(attempt(id, 3, PARKED, "UnknownProductException: bilinmeyen ürün"));
        log.record(attempt(id, 0, REPLAYED, null));
        log.record(attempt(id, 1, PROCESSED, null));

        assertThat(mine()).singleElement().satisfies(summary -> {
            assertThat(summary.state()).isEqualTo(PROCESSED);
            assertThat(summary.attempts()).isEqualTo(2);
            assertThat(summary.lastError()).as("düzelmiş mesajın son hatası hâlâ görülebilmeli").contains("bilinmeyen ürün");
        });
    }

    @Test
    void should_return_full_history_in_order() {
        String id = newId();
        log.record(attempt(id, 1, RETRY, "geçici"));
        log.record(attempt(id, 2, PROCESSED, null));
        log.record(attempt(id, 0, DUPLICATE, null));

        List<DeliveryEntry> history = log.history(id);

        assertThat(history).extracting(DeliveryEntry::outcome).containsExactly(RETRY, PROCESSED, DUPLICATE);
        assertThat(history).extracting(DeliveryEntry::attempt).containsExactly(1, 2, 0);
        assertThat(history.getFirst().error()).isEqualTo("geçici");
        assertThat(history).allSatisfy(entry -> assertThat(entry.at()).isNotNull());
        assertThat(log.history(newId())).isEmpty();
    }

    @Test
    void should_filter_by_current_state_and_message_id() {
        String processed = newId();
        String parked = newId();
        log.record(attempt(processed, 1, RETRY, "geçici"));
        log.record(attempt(processed, 2, PROCESSED, null));
        log.record(attempt(parked, 1, PARKED, "bozuk"));

        assertThat(log.find(new DeliveryQuery(sku, null, PARKED, 100)))
                .extracting(DeliverySummary::messageId).containsExactly(parked);
        assertThat(log.find(new DeliveryQuery(sku, null, RETRY, 100))).as("durum son denemeye göre").isEmpty();
        assertThat(log.find(new DeliveryQuery(null, processed, null, 100)))
                .extracting(DeliverySummary::state).containsExactly(PROCESSED);
    }

    @Test
    void should_keep_messages_without_id_apart() { // kimliksiz mesajlar birleştirilemez
        log.record(attempt(null, 1, PARKED, "message-id yok"));
        log.record(attempt(null, 1, PARKED, "message-id yok"));

        assertThat(mine()).hasSize(2).allSatisfy(summary -> assertThat(summary.messageId()).isNull());
    }

    @Test
    void should_list_most_recent_first_up_to_limit() {
        String first = newId();
        String second = newId();
        String third = newId();
        log.record(attempt(first, 1, PROCESSED, null));
        log.record(attempt(second, 1, PROCESSED, null));
        log.record(attempt(third, 1, PROCESSED, null));

        assertThat(log.find(new DeliveryQuery(sku, null, null, 2)))
                .extracting(DeliverySummary::messageId).containsExactly(third, second);
    }

    @Test
    void should_count_messages_by_current_state() {
        Map<DeliveryOutcome, Long> before = log.countByState();
        String id = newId();
        log.record(attempt(id, 1, RETRY, "geçici"));
        log.record(attempt(id, 2, PARKED, "geçici"));
        log.record(attempt(newId(), 1, PROCESSED, null));

        Map<DeliveryOutcome, Long> after = log.countByState();

        assertThat(after.getOrDefault(PARKED, 0L) - before.getOrDefault(PARKED, 0L)).isEqualTo(1);
        assertThat(after.getOrDefault(PROCESSED, 0L) - before.getOrDefault(PROCESSED, 0L)).isEqualTo(1);
        assertThat(after.getOrDefault(RETRY, 0L) - before.getOrDefault(RETRY, 0L)).isZero();
    }

    @Test
    void should_forget_attempts_older_than_retention() {
        String old = newId();
        String recent = newId();
        log.record(attempt(old, 1, PROCESSED, null));
        log.record(attempt(recent, 1, PROCESSED, null));
        jdbc.sql("update message_attempt set at = now() - interval '10 days' where message_id = ?").param(old).update();

        jdbcLog.forgetOlderThan(Duration.ofDays(7));

        assertThat(log.history(old)).isEmpty();
        assertThat(log.history(recent)).hasSize(1);
    }
}
