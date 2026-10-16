package com.rabbitlab.backoffice.adapter.in.web;

import com.rabbitlab.backoffice.application.CountStockCommand;
import com.rabbitlab.backoffice.application.CreateProductCommand;
import com.rabbitlab.backoffice.application.InventoryService;
import com.rabbitlab.backoffice.application.ProductService;
import com.rabbitlab.backoffice.application.UpdateProductCommand;
import com.rabbitlab.backoffice.domain.Sku;
import com.rabbitlab.backoffice.domain.product.Price;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.net.URI;

/**
 * Backoffice'in REST girişi (dış halka). Application servislerini doğrudan çağırır.
 *
 * <p>Doğrulama için ayrı bir katman (Bean Validation) yok: istek, domain'in value object'lerine
 * ({@link Sku}, {@link Price}) çevrilirken kurallar zaten çalışır; hata {@link ErrorHandler}'da 400 olur.
 * Kural tek yerde kalır.
 */
@RestController
@RequestMapping("/products")
class ProductController {

    private final ProductService products;
    private final InventoryService inventories;

    ProductController(ProductService products, InventoryService inventories) {
        this.products = products;
        this.inventories = inventories;
    }

    record CreateProductRequest(String sku, String name, String description, BigDecimal price, String currency) {
    }

    record UpdateProductRequest(String name, String description, BigDecimal price, String currency) {
    }

    record CountStockRequest(int quantity) {
    }

    @PostMapping
    ResponseEntity<Void> create(@RequestBody CreateProductRequest request) {
        Sku sku = new Sku(request.sku());
        products.create(new CreateProductCommand(sku, request.name(), request.description(),
                new Price(request.price(), request.currency())));
        return ResponseEntity.created(URI.create("/products/" + sku)).build();
    }

    @PutMapping("/{sku}")
    ResponseEntity<Void> update(@PathVariable String sku, @RequestBody UpdateProductRequest request) {
        products.update(new UpdateProductCommand(new Sku(sku), request.name(), request.description(),
                new Price(request.price(), request.currency())));
        return ResponseEntity.noContent().build();
    }

    /** Pasife alma bir "eylem"; silme olmadığı için DELETE değil. */
    @PostMapping("/{sku}/deactivate")
    ResponseEntity<Void> deactivate(@PathVariable String sku) {
        products.deactivate(new Sku(sku));
        return ResponseEntity.noContent().build();
    }

    /** Stok mutlak değerle girilir (sayım); bu yüzden PUT: aynı istek tekrarlanırsa sonuç değişmez. */
    @PutMapping("/{sku}/stock")
    ResponseEntity<Void> countStock(@PathVariable String sku, @RequestBody CountStockRequest request) {
        inventories.count(new CountStockCommand(new Sku(sku), request.quantity()));
        return ResponseEntity.noContent().build();
    }
}
