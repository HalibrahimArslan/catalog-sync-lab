package com.rabbitlab.backoffice.application;

import com.rabbitlab.backoffice.domain.Sku;
import com.rabbitlab.backoffice.domain.product.Product;
import com.rabbitlab.backoffice.domainservice.InventoryRepository;
import com.rabbitlab.backoffice.domainservice.ProductRegistration;
import com.rabbitlab.backoffice.domainservice.ProductRepository;

/**
 * Ürün use case'leri. Her metot aynı kalıbı izler: yükle → domain'e sor → kaydet → event'leri outbox'a yaz.
 * Kurallar domain'de (model ve domain servisleri); servis sadece akışı yönetir (orkestrasyon).
 *
 * <p>Inbound port arayüzü yok: REST controller bu sınıfı doğrudan çağırır. Faz 2'de her use case için
 * bir arayüz vardı ve her birinin tek bir uygulaması vardı; soyutlama hiçbir şeyi değiştirilebilir
 * kılmıyordu (acıtan nokta 1). Test için sahte servis gerekirse Mockito somut sınıfı da taklit eder.
 */
public final class ProductService {

    private final ProductRegistration registration;
    private final ProductRepository products;
    private final InventoryRepository inventories;
    private final EventOutbox outbox;
    private final Transaction transaction;

    public ProductService(ProductRegistration registration, ProductRepository products,
                          InventoryRepository inventories, EventOutbox outbox, Transaction transaction) {
        this.registration = registration;
        this.products = products;
        this.inventories = inventories;
        this.outbox = outbox;
        this.transaction = transaction;
    }

    public void create(CreateProductCommand command) {
        transaction.execute(() -> {
            ProductRegistration.Registered registered = registration.register(
                    command.sku(), command.name(), command.description(), command.price());
            products.save(registered.product());
            inventories.save(registered.inventory());
            outbox.append(registered.product().pullEvents());
        });
    }

    public void update(UpdateProductCommand command) {
        transaction.execute(() -> {
            Product product = products.get(command.sku());
            product.update(command.name(), command.description(), command.price());
            save(product);
        });
    }

    public void deactivate(Sku sku) {
        transaction.execute(() -> {
            Product product = products.get(sku);
            product.deactivate();
            save(product);
        });
    }

    private void save(Product product) {
        var events = product.pullEvents();
        if (events.isEmpty()) {
            return; // değişiklik yok
        }
        products.save(product);
        outbox.append(events);
    }
}
