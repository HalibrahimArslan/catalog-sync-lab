package com.rabbitlab.backoffice.domain.product;

import com.rabbitlab.backoffice.domain.AggregateRoot;
import com.rabbitlab.backoffice.domain.Require;
import com.rabbitlab.backoffice.domain.Sku;
import com.rabbitlab.backoffice.domain.event.ProductCreated;
import com.rabbitlab.backoffice.domain.event.ProductDeactivated;
import com.rabbitlab.backoffice.domain.event.ProductUpdated;

import java.util.Objects;

/**
 * Ürün aggregate'i: ürün bilgisinin (ad, açıklama, fiyat, aktiflik) asıl kaydı.
 *
 * <p>Her gerçek değişiklik versiyonu bir artırır ve bir domain event'i biriktirir. Hiçbir şeyi
 * değiştirmeyen komut versiyonu artırmaz; böylece Storefront'a gereksiz mesaj gitmez.
 * Stok bu aggregate'te YOK; o {@link com.rabbitlab.backoffice.domain.inventory.Inventory}'de.
 */
public final class Product extends AggregateRoot {

    private final Sku sku;
    private String name;
    private String description;
    private Price price;
    private boolean active;
    private long version;

    /** Yeni ürün. */
    private Product(Sku sku, String name, String description, Price price) {
        this.sku = Require.notNull(sku, "sku");
        this.name = Require.notBlank(name, "name");
        this.description = description;
        this.price = Require.notNull(price, "price");
        this.active = true;
        this.version = 1;
    }

    /** Kayıtlı ürün. */
    private Product(Sku sku, String name, String description, Price price, boolean active, long version) {
        super(version);
        this.sku = Require.notNull(sku, "sku");
        this.name = Require.notBlank(name, "name");
        this.description = description;
        this.price = Require.notNull(price, "price");
        this.active = active;
        this.version = version;
    }

    public static Product create(Sku sku, String name, String description, Price price) {
        Product product = new Product(sku, name, description, price);
        product.record(new ProductCreated(sku, name, description, price, product.version));
        return product;
    }

    /** Kayıtlı ürünü geri yükler (repository için). Event üretmez: geçmiş zaten yayınlandı. */
    public static Product restore(Sku sku, String name, String description, Price price, boolean active,
                                  long version) {
        return new Product(sku, name, description, price, active, version);
    }

    public void update(String name, String description, Price price) {
        if (!active) {
            throw new ProductInactiveException(sku);
        }
        Require.notBlank(name, "name");
        Require.notNull(price, "price");
        if (this.name.equals(name) && Objects.equals(this.description, description) && this.price.equals(price)) {
            return;
        }
        this.name = name;
        this.description = description;
        this.price = price;
        this.version++;
        record(new ProductUpdated(sku, name, description, price, version));
    }

    public void deactivate() {
        if (!active) {
            return;
        }
        this.active = false;
        this.version++;
        record(new ProductDeactivated(sku, version));
    }

    public Sku sku() {
        return sku;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public Price price() {
        return price;
    }

    public boolean active() {
        return active;
    }

    public long version() {
        return version;
    }
}
