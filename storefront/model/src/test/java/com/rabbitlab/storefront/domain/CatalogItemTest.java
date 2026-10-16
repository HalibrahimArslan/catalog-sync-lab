package com.rabbitlab.storefront.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P2-M4 — Storefront'taki ürün kopyası. Faz 1'deki {@code CatalogTest} davranışının aynısı, ama artık
 * Storefront'un kendi modeli: Backoffice'in sınıflarını da mesaj sözleşmesini de bilmiyor.
 *
 * <p>İki ayrı versiyon: ürün bilgisi ({@code detailsVersion}) ve stok ({@code stockVersion}). Gelen
 * versiyon mevcut olandan büyük değilse değişiklik yok sayılır: tekrar gelen ve geç gelen mesajlar zararsız.
 */
class CatalogItemTest {

    private static final Sku SKU = new Sku("SKU-1");

    private static Price try_(String amount) {
        return new Price(new BigDecimal(amount), "TRY");
    }

    private static CatalogItem kupa() {
        return CatalogItem.register(SKU, "Kupa", "Seramik kupa", try_("100"), 1);
    }

    @Test
    void should_register_active_and_out_of_stock() { // US1
        CatalogItem item = kupa();

        assertThat(item.active()).isTrue();
        assertThat(item.outOfStock()).isTrue();
        assertThat(item.detailsVersion()).isEqualTo(1);
        assertThat(item.stockVersion()).isZero();
    }

    @Test
    void should_apply_newer_details() { // US2
        CatalogItem item = kupa();

        boolean changed = item.changeDetails("Kupa", "Seramik kupa", try_("120"), 2);

        assertThat(changed).isTrue();
        assertThat(item.price()).isEqualTo(try_("120"));
        assertThat(item.detailsVersion()).isEqualTo(2);
    }

    @Test
    void should_ignore_older_details() { // US2
        CatalogItem item = kupa();
        item.changeDetails("Kupa", "Seramik kupa", try_("130"), 3);

        boolean changed = item.changeDetails("Kupa", "Seramik kupa", try_("120"), 2);

        assertThat(changed).isFalse();
        assertThat(item.price()).isEqualTo(try_("130"));
    }

    @Test
    void should_apply_newer_stock_and_ignore_repeated_or_older() { // US3
        CatalogItem item = kupa();

        assertThat(item.changeStock(8, 2)).isTrue();
        assertThat(item.changeStock(8, 2)).isFalse();   // aynı mesaj tekrar
        assertThat(item.changeStock(10, 1)).isFalse();  // geç gelen eski mesaj

        assertThat(item.stock()).isEqualTo(8);
        assertThat(item.stockVersion()).isEqualTo(2);
    }

    @Test
    void should_be_out_of_stock_at_zero() { // US3
        CatalogItem item = kupa();
        item.changeStock(1, 1);

        item.changeStock(0, 2);

        assertThat(item.outOfStock()).isTrue();
    }

    @Test
    void should_apply_stock_when_details_version_is_higher_than_stock_version() {
        // Tek versiyon olsaydı: fiyat v3 geldikten sonra stok v1 "eski" sanılıp yutulurdu.
        CatalogItem item = kupa();
        item.changeDetails("Kupa", "Seramik kupa", try_("120"), 3);

        assertThat(item.changeStock(5, 1)).isTrue();
        assertThat(item.stock()).isEqualTo(5);
    }

    @Test
    void should_hide_when_withdrawn() { // US4
        CatalogItem item = kupa();

        assertThat(item.withdraw(2)).isTrue();

        assertThat(item.active()).isFalse();
    }

    @Test
    void should_stay_inactive_when_late_stock_arrives() { // US4
        CatalogItem item = kupa();
        item.withdraw(2);

        item.changeStock(7, 1);

        assertThat(item.active()).isFalse();
        assertThat(item.stock()).isEqualTo(7); // stok bilgisi yine de güncel tutulur
    }

    @Test
    void should_ignore_details_older_than_withdrawal() {
        CatalogItem item = kupa();
        item.withdraw(3);

        assertThat(item.changeDetails("Kupa", "Seramik kupa", try_("90"), 2)).isFalse();
        assertThat(item.active()).isFalse();
    }

    @Test
    void should_reject_negative_stock() {
        assertThatThrownBy(() -> kupa().changeStock(-5, 1)).isInstanceOf(InvalidValueException.class);
    }

    @Test
    void should_reject_non_positive_price_and_version() {
        assertThatThrownBy(() -> try_("0")).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> CatalogItem.register(SKU, "Kupa", null, try_("1"), 0))
                .isInstanceOf(InvalidValueException.class);
    }
}
