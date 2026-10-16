package com.rabbitlab.storefront.adapter.out.persistence;

import com.rabbitlab.storefront.TestcontainersConfiguration;
import com.rabbitlab.storefront.application.CatalogService;
import com.rabbitlab.storefront.application.CatalogUpdate;
import com.rabbitlab.storefront.domain.CatalogItem;
import com.rabbitlab.storefront.domain.Price;
import com.rabbitlab.storefront.domain.Sku;
import com.rabbitlab.storefront.domainservice.CatalogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * P3-M4 — Inbox temizliği. İşlenmiş kimlikler sonsuza kadar tutulmaz.
 *
 * <p>Peki silinmiş bir kimlik tekrar gelirse? Inbox onu tanımaz ve değişiklik tekrar uygulanır;
 * ama versiyon kontrolü onu zararsız kılar. Inbox bir "erken çıkış", asıl güvence versiyonlar.
 * Saklama süresi, bir mesajın makul olarak tekrar gelebileceği en uzun süreden (relay tekrarları,
 * park kuyruğundan elle geri gönderme) uzun seçilmeli.
 */
@SpringBootTest(properties = {
        "storefront.inbox.retention=7d",
        "storefront.inbox.cleanup-interval=100ms"})
@Import(TestcontainersConfiguration.class)
class InboxCleanupIT {

    @Autowired
    JdbcClient jdbc;

    @Autowired
    CatalogService catalog;

    @Autowired
    CatalogRepository repository;

    @Test
    void should_forget_only_ids_older_than_retention() {
        String old = UUID.randomUUID().toString();
        String recent = UUID.randomUUID().toString();
        insertProcessed(old, "now() - interval '10 days'");
        insertProcessed(recent, "now() - interval '1 hour'");

        await().atMost(Duration.ofSeconds(5)).until(() -> !isRemembered(old));

        assertThat(isRemembered(recent)).isTrue();
    }

    @Test
    void should_stay_correct_when_forgotten_update_arrives_again() {
        Sku sku = new Sku("SKU-" + UUID.randomUUID().toString().substring(0, 8));
        catalog.apply(UUID.randomUUID().toString(),
                new CatalogUpdate.NewProduct(sku, "Kupa", null, new Price(new BigDecimal("100"), "TRY"), 1));
        String stockUpdateId = UUID.randomUUID().toString();
        catalog.apply(stockUpdateId, new CatalogUpdate.StockChanged(sku, 8, 1));
        catalog.apply(UUID.randomUUID().toString(), new CatalogUpdate.StockChanged(sku, 5, 2));

        // Kimliği eskit ve temizlenmesini bekle: inbox artık bu değişikliği hatırlamıyor.
        jdbc.sql("update processed_message set processed_at = now() - interval '10 days' where message_id = ?")
                .param(stockUpdateId).update();
        await().atMost(Duration.ofSeconds(5)).until(() -> !isRemembered(stockUpdateId));

        catalog.apply(stockUpdateId, new CatalogUpdate.StockChanged(sku, 8, 1)); // çok geç gelen tekrar

        CatalogItem item = repository.find(sku).orElseThrow();
        assertThat(item.stock()).as("v1 < v2: versiyon kontrolü eski değişikliği yok saydı").isEqualTo(5);
        assertThat(isRemembered(stockUpdateId)).as("tekrar işlendi, tekrar hatırlanıyor").isTrue();
    }

    private void insertProcessed(String id, String processedAt) {
        jdbc.sql("insert into processed_message (message_id, processed_at) values (?, %s)".formatted(processedAt))
                .param(id).update();
    }

    private boolean isRemembered(String id) {
        return jdbc.sql("select count(*) from processed_message where message_id = ?").param(id)
                .query(Integer.class).single() > 0;
    }
}
