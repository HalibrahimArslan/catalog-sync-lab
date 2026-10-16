package com.rabbitlab.backoffice.domainservice;

import com.rabbitlab.backoffice.domain.Sku;
import com.rabbitlab.backoffice.domain.inventory.Inventory;
import com.rabbitlab.backoffice.domain.product.Price;
import com.rabbitlab.backoffice.domain.product.Product;

/**
 * Domain servisi: yeni ürün kaydı. İki kural, ikisi de tek bir aggregate'e sığmıyor:
 * <ul>
 *   <li>SKU benzersizdir: {@code Product} diğer ürünleri göremez, repository'ye sormak gerekir.</li>
 *   <li>Ürün, boş stoğuyla birlikte açılır: iki aggregate birden doğar.</li>
 * </ul>
 * Kaydetmez, event yayınlamaz: yeni aggregate'leri üretip döner. Kalıcılık ve transaction application
 * servisinin işi. Böylece kural, hangi akışta kullanılırsa kullanılsın aynı kalır.
 */
public final class ProductRegistration {

    private final ProductRepository products;

    public ProductRegistration(ProductRepository products) {
        this.products = products;
    }

    public record Registered(Product product, Inventory inventory) {
    }

    /** @throws DuplicateSkuException SKU alınmışsa */
    public Registered register(Sku sku, String name, String description, Price price) {
        if (products.findBySku(sku).isPresent()) {
            throw new DuplicateSkuException(sku);
        }
        return new Registered(Product.create(sku, name, description, price), Inventory.open(sku));
    }
}
