package com.rabbitlab.storefront.adapter.out.persistence;

import com.rabbitlab.storefront.TestcontainersConfiguration;
import com.rabbitlab.storefront.application.CatalogQueries;
import com.rabbitlab.storefront.application.CatalogService;
import com.rabbitlab.storefront.application.CatalogView;
import com.rabbitlab.storefront.domainservice.CatalogRepository;
import com.rabbitlab.storefront.application.CatalogUpdate;
import com.rabbitlab.storefront.domain.CatalogItem;
import com.rabbitlab.storefront.domain.Price;
import com.rabbitlab.storefront.domain.Sku;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P2-M4 — Katalog artık PostgreSQL'de (Faz 1'de bellekteydi; uygulama kapanınca vitrin boşalıyordu).
 * Birden fazla consumer aynı ürünü aynı anda güncellese de hiçbir güncelleme kaybolmuyor.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CatalogPersistenceIT {

    @Autowired
    CatalogService catalog;

    @Autowired
    CatalogRepository repository;

    @Autowired
    CatalogQueries queries;

    @Autowired
    JdbcClient jdbc;

    private final Sku sku = new Sku("SKU-" + UUID.randomUUID().toString().substring(0, 8));

    private static String newId() {
        return UUID.randomUUID().toString();
    }

    private static Price try_(String amount) {
        return new Price(new BigDecimal(amount), "TRY");
    }

    @Test
    void should_store_catalog_item_in_database() {
        catalog.apply(newId(), new CatalogUpdate.NewProduct(sku, "Kupa", "Seramik kupa", try_("100"), 1));
        catalog.apply(newId(), new CatalogUpdate.StockChanged(sku, 8, 1));

        // Doğrudan tabloya bakıyoruz: bilgi gerçekten kalıcı.
        var row = jdbc.sql("select * from catalog_item where sku = ?").param(sku.value()).query().singleRow();
        assertThat(row.get("name")).isEqualTo("Kupa");
        assertThat(row.get("stock")).isEqualTo(8);
        assertThat(row.get("details_version")).isEqualTo(1L);
        assertThat(row.get("stock_version")).isEqualTo(1L);

        CatalogItem item = repository.find(sku).orElseThrow();
        assertThat(item.price()).isEqualTo(try_("100"));
        assertThat(item.active()).isTrue();
    }

    @Test
    void should_keep_single_row_when_same_product_arrives_concurrently() {
        // At-least-once: aynı ProductCreated iki consumer'a aynı anda gelebilir.
        var command = new CatalogUpdate.NewProduct(sku, "Kupa", "Seramik kupa", try_("100"), 1);

        CompletableFuture.allOf(
                CompletableFuture.runAsync(() -> catalog.apply(newId(), command)),
                CompletableFuture.runAsync(() -> catalog.apply(newId(), command))).join();

        assertThat(jdbc.sql("select count(*) from catalog_item where sku = ?").param(sku.value())
                .query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    void should_not_lose_updates_when_details_and_stock_change_concurrently() throws Exception {
        // Katalog kuyruğu ve stok kuyruğu consumer'ları aynı satırı aynı anda güncelliyor.
        // Kilit olmasaydı: ikisi de satırı okur, biri fiyatı biri stoğu değiştirip TÜM satırı yazar;
        // sonra yazan, öncekinin değişikliğini ezer (lost update).
        catalog.apply(newId(), new CatalogUpdate.NewProduct(sku, "Kupa", "Seramik kupa", try_("100"), 1));
        List<CatalogUpdate> updates = new ArrayList<>();
        for (int v = 1; v <= 20; v++) {
            updates.add(new CatalogUpdate.StockChanged(sku, v * 10, v));
            updates.add(new CatalogUpdate.DetailsChanged(sku, "Kupa", null, try_(String.valueOf(100 + v)), v + 1));
        }
        Collections.shuffle(updates); // sıra da bozuk gelsin

        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            CompletableFuture.allOf(updates.stream()
                    .map(u -> CompletableFuture.runAsync(() -> catalog.apply(newId(), u), pool))
                    .toArray(CompletableFuture[]::new)).join();
        }

        CatalogItem item = repository.find(sku).orElseThrow();
        assertThat(item.stock()).isEqualTo(200);
        assertThat(item.stockVersion()).isEqualTo(20);
        assertThat(item.price()).isEqualTo(try_("120"));
        assertThat(item.detailsVersion()).isEqualTo(21);
    }

    @Test
    void should_list_only_active_items() {
        Sku other = new Sku(sku.value() + "-B");
        catalog.apply(newId(), new CatalogUpdate.NewProduct(sku, "Kupa", null, try_("100"), 1));
        catalog.apply(newId(), new CatalogUpdate.NewProduct(other, "Tabak", null, try_("50"), 1));

        catalog.apply(newId(), new CatalogUpdate.Withdrawn(other, 2));

        assertThat(queries.visible()).extracting(CatalogView::sku).contains(sku.value()).doesNotContain(other.value());
    }
}
