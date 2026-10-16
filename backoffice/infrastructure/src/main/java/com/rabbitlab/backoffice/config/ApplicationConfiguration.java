package com.rabbitlab.backoffice.config;

import com.rabbitlab.backoffice.application.InventoryService;
import com.rabbitlab.backoffice.application.ProductService;
import com.rabbitlab.backoffice.application.EventOutbox;
import com.rabbitlab.backoffice.application.Transaction;
import com.rabbitlab.backoffice.domainservice.InventoryRepository;
import com.rabbitlab.backoffice.domainservice.ProductRegistration;
import com.rabbitlab.backoffice.domainservice.ProductRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring: application servisleri Spring annotation'ı taşımıyor (framework'süz kalsınlar diye);
 * onları burada elle bean yapıyoruz. Adapter'lar ise {@code @Component} ile kendiliğinden bulunuyor.
 */
@Configuration(proxyBeanMethods = false)
class ApplicationConfiguration {

    @Bean
    ProductRegistration productRegistration(ProductRepository products) {
        return new ProductRegistration(products);
    }

    @Bean
    ProductService productService(ProductRegistration registration, ProductRepository products,
                                  InventoryRepository inventories, EventOutbox outbox, Transaction transaction) {
        return new ProductService(registration, products, inventories, outbox, transaction);
    }

    @Bean
    InventoryService inventoryService(InventoryRepository inventories, EventOutbox outbox, Transaction transaction) {
        return new InventoryService(inventories, outbox, transaction);
    }
}
