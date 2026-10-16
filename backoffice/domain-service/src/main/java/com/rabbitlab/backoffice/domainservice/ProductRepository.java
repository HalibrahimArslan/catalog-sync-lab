package com.rabbitlab.backoffice.domainservice;

import com.rabbitlab.backoffice.domain.Sku;
import com.rabbitlab.backoffice.domain.product.Product;

import java.util.Optional;

/**
 * "Ürünler kalıcıdır" sözleşmesi. Onion'da repository arayüzü domain servisleri halkasındadır
 * (Faz 2'de application'ın port.out'undaydı): kalıcılık bir iş kavramı, nasıl yapıldığı değil.
 */
public interface ProductRepository {

    Optional<Product> findBySku(Sku sku);

    /** @throws ProductNotFoundException ürün yoksa */
    default Product get(Sku sku) {
        return findBySku(sku).orElseThrow(() -> new ProductNotFoundException(sku));
    }

    /**
     * Yeni ürünü ekler veya var olanı günceller.
     *
     * @throws ConcurrentUpdateException ürün okunduktan sonra başkası tarafından değiştirildiyse
     */
    void save(Product product);
}
