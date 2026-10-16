package com.rabbitlab.storefront.domainservice;

import com.rabbitlab.storefront.domain.CatalogItem;
import com.rabbitlab.storefront.domain.Sku;

import java.util.Optional;

/** Katalog kalıcıdır. Onion'da repository arayüzü domain servisleri halkasında. */
public interface CatalogRepository {

    Optional<CatalogItem> find(Sku sku);

    /**
     * Ürünü okur ve transaction bitene kadar kilitler. Aynı ürünü güncellemek isteyen diğer
     * consumer'lar sırada bekler; böylece kimse diğerinin değişikliğini ezmez.
     */
    Optional<CatalogItem> findForUpdate(Sku sku);

    /** {@link #findForUpdate} ile aynı; ürün yoksa {@link UnknownProductException}. */
    default CatalogItem getForUpdate(Sku sku) {
        return findForUpdate(sku).orElseThrow(() -> new UnknownProductException(sku));
    }

    /** @return eklendiyse {@code true}; aynı SKU zaten varsa hiçbir şey yapmaz ve {@code false} döner */
    boolean insertIfAbsent(CatalogItem item);

    void update(CatalogItem item);
}
