package com.rabbitlab.storefront.adapter.out.persistence;

import com.rabbitlab.storefront.TestcontainersConfiguration;
import com.rabbitlab.storefront.application.CatalogQueries;
import com.rabbitlab.storefront.application.CatalogService;
import com.rabbitlab.storefront.application.CatalogUpdate;
import com.rabbitlab.storefront.application.CatalogView;
import com.rabbitlab.storefront.domain.Price;
import com.rabbitlab.storefront.domain.Sku;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P3-M3 — Okuma tarafı. Yazma {@link CatalogService} → domain → repository yolundan geçer; okuma ise
 * doğrudan tablodan, vitrinin ihtiyacı olan şekilde ({@link CatalogView}) gelir. Domain nesnesi
 * okuyana hiç verilmez; okuyan {@code changeStock(...)} çağıramaz (Faz 2 acıtan nokta 4).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CatalogQueriesIT {

    @Autowired
    CatalogService catalog;

    @Autowired
    CatalogQueries queries;

    private final String sku = "SKU-" + UUID.randomUUID().toString().substring(0, 8);

    private void apply(CatalogUpdate update) {
        catalog.apply(UUID.randomUUID().toString(), update);
    }

    private static Price try_(String amount) {
        return new Price(new BigDecimal(amount), "TRY");
    }

    @Test
    void should_read_item_as_view() {
        apply(new CatalogUpdate.NewProduct(new Sku(sku), "Kupa", "Seramik kupa", try_("100"), 1));
        apply(new CatalogUpdate.StockChanged(new Sku(sku), 8, 1));

        assertThat(queries.find(sku)).hasValue(
                new CatalogView(sku, "Kupa", "Seramik kupa", new BigDecimal("100.00"), "TRY", 8, false, true));
    }

    @Test
    void should_mark_item_without_stock_as_out_of_stock() {
        apply(new CatalogUpdate.NewProduct(new Sku(sku), "Kupa", null, try_("100"), 1));

        assertThat(queries.find(sku)).hasValueSatisfying(view -> assertThat(view.outOfStock()).isTrue());
    }

    @Test
    void should_find_nothing_for_unknown_sku() {
        assertThat(queries.find(sku)).isEmpty();
    }

    @Test
    void should_list_only_active_items_in_sku_order() {
        String b = sku + "-B";
        String a = sku + "-A";
        String hidden = sku + "-C";
        apply(new CatalogUpdate.NewProduct(new Sku(b), "Tabak", null, try_("50"), 1));
        apply(new CatalogUpdate.NewProduct(new Sku(a), "Kupa", null, try_("100"), 1));
        apply(new CatalogUpdate.NewProduct(new Sku(hidden), "Bardak", null, try_("30"), 1));
        apply(new CatalogUpdate.Withdrawn(new Sku(hidden), 2));

        assertThat(queries.visible()).extracting(CatalogView::sku)
                .filteredOn(s -> s.startsWith(sku))
                .containsExactly(a, b);
    }
}
