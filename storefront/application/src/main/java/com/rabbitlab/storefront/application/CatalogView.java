package com.rabbitlab.storefront.application;

import java.math.BigDecimal;

/**
 * Vitrinde bir ürünün görünümü: okuma tarafının modeli. Domain nesnesi ({@code CatalogItem}) değil;
 * davranışı yok, değiştirilemez, vitrinin ihtiyacı kadar alan taşır (versiyonlar yok).
 *
 * <p>{@code outOfStock}, domain'deki "stok 0 ise Tükendi" kuralının okuma tarafındaki kopyası. Basit
 * türetilmiş bilgilerde bu tekrar CQRS'in bilinen bedeli; kural karmaşıklaşırsa yazma tarafı
 * sonucu ayrı bir sütuna yazar.
 */
public record CatalogView(String sku, String name, String description, BigDecimal price, String currency,
                          int stock, boolean outOfStock, boolean active) {
}
