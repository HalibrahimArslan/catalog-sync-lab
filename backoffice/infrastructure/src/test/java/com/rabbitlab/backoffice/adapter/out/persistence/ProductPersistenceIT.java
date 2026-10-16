package com.rabbitlab.backoffice.adapter.out.persistence;

import com.rabbitlab.backoffice.application.InventoryService;
import com.rabbitlab.backoffice.application.ProductService;
import com.rabbitlab.backoffice.TestcontainersConfiguration;
import com.rabbitlab.backoffice.application.CountStockCommand;
import com.rabbitlab.backoffice.application.CreateProductCommand;
import com.rabbitlab.backoffice.application.EventOutbox;
import com.rabbitlab.backoffice.domain.Sku;
import com.rabbitlab.backoffice.domain.product.Price;
import com.rabbitlab.backoffice.domain.product.Product;
import com.rabbitlab.backoffice.domainservice.ConcurrentUpdateException;
import com.rabbitlab.backoffice.domainservice.DuplicateSkuException;
import com.rabbitlab.backoffice.domainservice.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;

/**
 * P2-M2 — Aggregate ve outbox AYNI transaction'da yazılır.
 *
 * <p>Faz 1'deki açık: "DB'ye yazdım ama mesajı gönderemedim" (veya tersi). Outbox ile mesaj,
 * aggregate ile aynı veritabanı transaction'ında bir tabloya yazılır: ya ikisi birden olur ya hiçbiri.
 * Mesajı RabbitMQ'ya taşımak ayrı bir işin (relay, P2-M3) görevi.
 *
 * <p>Testler aynı veritabanını paylaşıyor; her test kendi SKU'sunu kullanarak izole kalıyor.
 * Relay kapalı: outbox satırının "yayınlanmamış" hâlini görmek istiyoruz.
 */
@SpringBootTest(properties = "backoffice.outbox.scheduling-enabled=false")
@Import(TestcontainersConfiguration.class)
class ProductPersistenceIT {

    @Autowired
    ProductService productService;

    @Autowired
    InventoryService inventoryService;

    @Autowired
    ProductRepository products;

    @Autowired
    JdbcClient jdbc;

    @MockitoSpyBean
    EventOutbox outbox;

    private final Sku sku = new Sku("SKU-" + UUID.randomUUID().toString().substring(0, 8));
    private final Price price = new Price(new BigDecimal("100"), "TRY");

    @Test
    void should_store_product_inventory_and_outbox_message_together() {
        productService.create(new CreateProductCommand(sku, "Kupa", "Seramik kupa", price));

        Product stored = products.findBySku(sku).orElseThrow();
        assertThat(stored.name()).isEqualTo("Kupa");
        assertThat(stored.price()).isEqualTo(price);
        assertThat(stored.active()).isTrue();
        assertThat(stored.version()).isEqualTo(1);
        assertThat(inventoryQuantity()).isZero();

        assertThat(outboxRows()).singleElement().satisfies(row -> {
            assertThat(row.get("message_type")).isEqualTo("ProductCreated");
            assertThat(row.get("routing_key")).isEqualTo("product.created");
            assertThat(row.get("message_id")).isNotNull();
            assertThat(row.get("published_at")).isNull();
            assertThat(row.get("payload").toString())
                    .contains("\"sku\": \"" + sku + "\"", "\"price\": 100.00", "\"currency\": \"TRY\"", "\"version\": 1");
        });
    }

    @Test
    void should_store_stock_count_with_its_own_version() {
        productService.create(new CreateProductCommand(sku, "Kupa", "Seramik kupa", price));

        inventoryService.count(new CountStockCommand(sku, 8));

        assertThat(inventoryQuantity()).isEqualTo(8);
        assertThat(outboxRows()).extracting(row -> row.get("message_type"))
                .containsExactly("ProductCreated", "StockUpdated");
        assertThat(outboxRows().get(1).get("payload").toString()).contains("\"quantity\": 8", "\"version\": 1");
    }

    @Test
    void should_not_store_product_when_outbox_write_fails() {
        doThrow(new IllegalStateException("outbox yazılamadı")).when(outbox).append(anyList());

        assertThatThrownBy(() -> productService.create(new CreateProductCommand(sku, "Kupa", "Seramik kupa", price)))
                .hasMessageContaining("outbox yazılamadı");

        // Rollback: event yoksa ürün de yok. Storefront'un hiç duymayacağı bir ürün oluşmadı.
        assertThat(products.findBySku(sku)).isEmpty();
        assertThat(jdbc.sql("select count(*) from inventory where sku = ?").param(sku.value())
                .query(Integer.class).single()).isZero();
    }

    @Test
    void should_reject_save_of_stale_copy() {
        // İki ürün yöneticisi aynı ürünü aynı anda açtı (ikisi de v1 gördü).
        productService.create(new CreateProductCommand(sku, "Kupa", "Seramik kupa", price));
        Product first = products.findBySku(sku).orElseThrow();
        Product second = products.findBySku(sku).orElseThrow();

        first.update("Kupa", "Seramik kupa", new Price(new BigDecimal("120"), "TRY"));
        products.save(first);
        second.update("Kupa", "Seramik kupa", new Price(new BigDecimal("90"), "TRY"));

        // Optimistic locking olmasaydı ikisi de "v2" yayınlardı; Storefront ikincisini yok sayar,
        // Backoffice 90 der vitrin 120 derdi. Kalıcı tutarsızlık.
        assertThatThrownBy(() -> products.save(second))
                .isInstanceOf(ConcurrentUpdateException.class)
                .hasMessageContaining(sku.value());
        assertThat(products.findBySku(sku).orElseThrow().price().amount()).isEqualByComparingTo("120");
    }

    @Test
    void should_reject_duplicate_sku_at_database_level() {
        // Servisteki "var mı?" kontrolünü atlayıp doğrudan repository'ye gidiyoruz: aynı anda gelen
        // iki isteğin ikisi de kontrolü geçmiş gibi.
        products.save(Product.create(sku, "Kupa", "Seramik kupa", price));

        assertThatThrownBy(() -> products.save(Product.create(sku, "Başka", null, price)))
                .isInstanceOf(DuplicateSkuException.class);
    }

    private int inventoryQuantity() {
        return jdbc.sql("select quantity from inventory where sku = ?").param(sku.value()).query(Integer.class).single();
    }

    private List<Map<String, Object>> outboxRows() {
        return jdbc.sql("select * from outbox where sku = ? order by id").param(sku.value()).query().listOfRows();
    }
}
