package com.rabbitlab.backoffice.adapter.out.outbox;

import com.rabbitlab.backoffice.TestcontainersConfiguration;
import com.rabbitlab.backoffice.application.CountStockCommand;
import com.rabbitlab.backoffice.application.CreateProductCommand;
import com.rabbitlab.backoffice.application.InventoryService;
import com.rabbitlab.backoffice.application.OutboxEntry;
import com.rabbitlab.backoffice.application.OutboxQueries;
import com.rabbitlab.backoffice.application.OutboxQuery;
import com.rabbitlab.backoffice.application.OutboxState;
import com.rabbitlab.backoffice.application.ProductService;
import com.rabbitlab.backoffice.domain.Sku;
import com.rabbitlab.backoffice.domain.product.Price;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.rabbitlab.backoffice.application.OutboxState.FAILING;
import static com.rabbitlab.backoffice.application.OutboxState.PENDING;
import static com.rabbitlab.backoffice.application.OutboxState.PUBLISHED;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mesaj takibinin Backoffice tarafı: outbox'taki her mesajın durumu. Relay bu testte kapalı; durumları
 * elle değiştiriyoruz.
 *
 * <ul>
 *   <li>PENDING: yazıldı, henüz denenmedi (relay'in bir sonraki turunu bekliyor)</li>
 *   <li>FAILING: en az bir kez gönderilemedi; {@code last_error} sebebi söyler (ör. NO_ROUTE)</li>
 *   <li>PUBLISHED: broker onayladı ve bir kuyruğa ulaştı</li>
 * </ul>
 */
@SpringBootTest(properties = "backoffice.outbox.scheduling-enabled=false")
@Import(TestcontainersConfiguration.class)
class OutboxQueriesIT {

    @Autowired
    OutboxQueries queries;

    @Autowired
    ProductService products;

    @Autowired
    InventoryService inventories;

    @Autowired
    JdbcClient jdbc;

    private final Sku sku = new Sku("SKU-" + UUID.randomUUID().toString().substring(0, 8));

    private List<OutboxEntry> mine() {
        return queries.find(new OutboxQuery(sku.value(), null, null, 100));
    }

    private void createProduct() {
        products.create(new CreateProductCommand(sku, "Kupa", "Seramik kupa", new Price(new BigDecimal("100"), "TRY")));
    }

    @Test
    void should_show_written_message_as_pending_with_its_content() {
        createProduct();

        assertThat(mine()).singleElement().satisfies(entry -> {
            assertThat(entry.state()).isEqualTo(PENDING);
            assertThat(entry.type()).isEqualTo("ProductCreated");
            assertThat(entry.routingKey()).isEqualTo("product.created");
            assertThat(entry.schemaVersion()).isEqualTo(1);
            assertThat(entry.payload()).contains(sku.value(), "Kupa");
            assertThat(entry.messageId()).isNotBlank();
            assertThat(entry.createdAt()).isNotNull();
            assertThat(entry.publishedAt()).isNull();
            assertThat(entry.attempts()).isZero();
            assertThat(entry.lastError()).isNull();
        });
    }

    @Test
    void should_show_failing_message_with_last_error() {
        createProduct();
        jdbc.sql("update outbox set attempts = 2, last_error = 'geri döndü: NO_ROUTE (312)' where sku = ?")
                .param(sku.value()).update();

        assertThat(mine()).singleElement().satisfies(entry -> {
            assertThat(entry.state()).isEqualTo(FAILING);
            assertThat(entry.attempts()).isEqualTo(2);
            assertThat(entry.lastError()).contains("NO_ROUTE");
        });
    }

    @Test
    void should_show_published_message_with_time() {
        createProduct();
        jdbc.sql("update outbox set published_at = now() where sku = ?").param(sku.value()).update();

        assertThat(mine()).singleElement().satisfies(entry -> {
            assertThat(entry.state()).isEqualTo(PUBLISHED);
            assertThat(entry.publishedAt()).isNotNull();
        });
    }

    @Test
    void should_filter_by_state_and_message_id_newest_first() {
        createProduct();
        inventories.count(new CountStockCommand(sku, 5));
        jdbc.sql("update outbox set published_at = now() where sku = ? and message_type = 'ProductCreated'")
                .param(sku.value()).update();

        assertThat(mine()).extracting(OutboxEntry::type).containsExactly("StockUpdated", "ProductCreated");
        assertThat(queries.find(new OutboxQuery(sku.value(), null, PENDING, 100)))
                .extracting(OutboxEntry::type).containsExactly("StockUpdated");
        String id = mine().getLast().messageId();
        assertThat(queries.find(new OutboxQuery(null, id, null, 100)))
                .extracting(OutboxEntry::type).containsExactly("ProductCreated");
        assertThat(queries.find(new OutboxQuery(sku.value(), null, null, 1))).hasSize(1);
    }

    @Test
    void should_count_messages_by_state() {
        Map<OutboxState, Long> before = queries.countByState();
        createProduct();
        inventories.count(new CountStockCommand(sku, 5));
        jdbc.sql("update outbox set attempts = 1, last_error = 'x' where sku = ? and message_type = 'StockUpdated'")
                .param(sku.value()).update();

        Map<OutboxState, Long> after = queries.countByState();

        assertThat(after.getOrDefault(PENDING, 0L) - before.getOrDefault(PENDING, 0L)).isEqualTo(1);
        assertThat(after.getOrDefault(FAILING, 0L) - before.getOrDefault(FAILING, 0L)).isEqualTo(1);
    }
}
