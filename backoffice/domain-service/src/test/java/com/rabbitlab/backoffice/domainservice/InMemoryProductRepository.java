package com.rabbitlab.backoffice.domainservice;

import com.rabbitlab.backoffice.domain.Sku;
import com.rabbitlab.backoffice.domain.product.Product;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Repository'nin sahte (fake) uygulaması. Domain servislerini ve application servislerini veritabanı
 * olmadan test etmek için. Bu modülün test-jar'ı ile application modülünün testleri de kullanır.
 */
public class InMemoryProductRepository implements ProductRepository {

    public final Map<Sku, Product> saved = new HashMap<>();

    @Override
    public Optional<Product> findBySku(Sku sku) {
        return Optional.ofNullable(saved.get(sku));
    }

    @Override
    public void save(Product product) {
        saved.put(product.sku(), product);
    }
}
