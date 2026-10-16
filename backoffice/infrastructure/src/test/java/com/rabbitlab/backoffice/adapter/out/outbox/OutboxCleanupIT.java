package com.rabbitlab.backoffice.adapter.out.outbox;

import com.rabbitlab.backoffice.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * P3-M4 — Outbox temizliği. Yayınlanmış satırların işi bitti; ama hemen silinmezler, bir süre
 * (retention) hata ayıklamak için tutulurlar ("bu ürünün dün gönderilen mesajı neydi?").
 * Yayınlanmamış satır ne kadar eski olursa olsun silinmez: o hâlâ gönderilmeyi bekleyen bir mesaj.
 *
 * <p>Relay kapalı (yayınlanmamış satır yayınlanmasın); temizlik zamanlayıcısı 100 ms'de bir dönüyor.
 */
@SpringBootTest(properties = {
        "backoffice.outbox.scheduling-enabled=false",
        "backoffice.outbox.retention=7d",
        "backoffice.outbox.cleanup-interval=100ms"})
@Import(TestcontainersConfiguration.class)
class OutboxCleanupIT {

    @Autowired
    JdbcClient jdbc;

    @Test
    void should_delete_only_published_rows_older_than_retention() {
        String oldPublished = insert("now() - interval '10 days'", "now() - interval '10 days'");
        String recentPublished = insert("now() - interval '1 hour'", "now() - interval '1 hour'");
        String oldUnpublished = insert("now() - interval '10 days'", "null");

        await().atMost(Duration.ofSeconds(5)).until(() -> !exists(oldPublished));

        assertThat(exists(recentPublished)).as("saklama süresi dolmamış").isTrue();
        assertThat(exists(oldUnpublished)).as("henüz gönderilmemiş").isTrue();
    }

    private String insert(String createdAt, String publishedAt) {
        String sku = "SKU-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.sql("""
                        insert into outbox (message_id, sku, message_type, routing_key, payload, created_at, published_at)
                        values (?, ?, 'ProductCreated', 'product.created', '{}'::jsonb, %s, %s)"""
                        .formatted(createdAt, publishedAt))
                .params(UUID.randomUUID(), sku)
                .update();
        return sku;
    }

    private boolean exists(String sku) {
        return jdbc.sql("select count(*) from outbox where sku = ?").param(sku).query(Integer.class).single() > 0;
    }
}
