package com.rabbitlab.storefront.domainservice;

import com.rabbitlab.storefront.domain.NotFoundException;
import com.rabbitlab.storefront.domain.Sku;

/**
 * Katalogda olmayan ürün için değişiklik geldi. Genelde geçicidir: ürünün kendisi henüz gelmemiş
 * olabilir (katalog ve stok kuyrukları arasında sıra garantisi yok). Tekrar denemeye değer.
 */
public class UnknownProductException extends NotFoundException {

    public UnknownProductException(Sku sku) {
        super("Katalogda bilinmeyen ürün: " + sku);
    }
}
