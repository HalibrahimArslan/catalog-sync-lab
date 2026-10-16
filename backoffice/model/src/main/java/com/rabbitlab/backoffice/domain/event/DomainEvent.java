package com.rabbitlab.backoffice.domain.event;

import com.rabbitlab.backoffice.domain.Sku;

/**
 * Backoffice'te olmuş bir iş olayı. Geçmiş zaman: "oldu", geri alınamaz.
 *
 * <p>DİKKAT: Bu, mesaj sözleşmesi DEĞİL. Kablodaki JSON {@code contract} modülünde tanımlı;
 * dönüşümü messaging adapter'ı yapar (P2-M3). Böylece domain'de bir alanın adını değiştirmek
 * Storefront'u bozmaz.
 *
 * <p>{@code sealed}: adapter'daki {@code switch}, yeni bir event eklenip unutulursa derlenmez.
 */
public sealed interface DomainEvent permits ProductCreated, ProductUpdated, ProductDeactivated, StockUpdated {

    Sku sku();

    /** Olayı üreten aggregate'in, olaydan sonraki versiyonu. */
    long version();
}
