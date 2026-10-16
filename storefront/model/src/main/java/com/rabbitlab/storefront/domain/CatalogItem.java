package com.rabbitlab.storefront.domain;

/**
 * Vitrindeki ürün kopyası.
 *
 * <p>İki ayrı versiyon tutar: ürün bilgisi ({@code detailsVersion}) ve stok ({@code stockVersion}).
 * Gelen versiyon mevcut olandan büyük değilse değişiklik yok sayılır; tekrar gelen ve geç gelen
 * mesajlar böylece zararsız olur. Her metot "bir şey değişti mi?" sorusunu cevaplar; değişmediyse
 * veritabanına yazmaya gerek yok.
 */
public final class CatalogItem {

    private final Sku sku;
    private String name;
    private String description;
    private Price price;
    private int stock;
    private boolean active;
    private long detailsVersion;
    private long stockVersion;

    private CatalogItem(Sku sku, String name, String description, Price price, int stock, boolean active,
                        long detailsVersion, long stockVersion) {
        if (sku == null) {
            throw new InvalidValueException("sku boş olamaz");
        }
        this.sku = sku;
        this.name = requireName(name);
        this.description = description;
        this.price = requirePrice(price);
        this.stock = requireStock(stock);
        this.active = active;
        this.detailsVersion = detailsVersion;
        this.stockVersion = stockVersion;
    }

    /** Yeni ürün vitrine eklenir: aktif ve "Tükendi". Stok ayrı mesajla gelir. */
    public static CatalogItem register(Sku sku, String name, String description, Price price, long version) {
        return new CatalogItem(sku, name, description, price, 0, true, requireVersion(version), 0);
    }

    /** Kayıtlı ürünü geri yükler (repository için). */
    public static CatalogItem restore(Sku sku, String name, String description, Price price, int stock,
                                      boolean active, long detailsVersion, long stockVersion) {
        return new CatalogItem(sku, name, description, price, stock, active, detailsVersion, stockVersion);
    }

    public boolean changeDetails(String name, String description, Price price, long version) {
        requireName(name);
        requirePrice(price);
        if (requireVersion(version) <= detailsVersion) {
            return false;
        }
        this.name = name;
        this.description = description;
        this.price = price;
        this.detailsVersion = version;
        return true;
    }

    /** Pasif ürünün stoğu da güncel tutulur; ama ürün pasif kalır (tekrar aktifleştirme kapsam dışı). */
    public boolean changeStock(int quantity, long version) {
        requireStock(quantity);
        if (requireVersion(version) <= stockVersion) {
            return false;
        }
        this.stock = quantity;
        this.stockVersion = version;
        return true;
    }

    /** Satıştan kaldırıldı. Ürün bilgisi versiyonunu taşır: kendisinden eski bilgi güncellemeleri yok sayılır. */
    public boolean withdraw(long version) {
        if (requireVersion(version) <= detailsVersion) {
            return false;
        }
        this.active = false;
        this.detailsVersion = version;
        return true;
    }

    public boolean outOfStock() {
        return stock == 0;
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new InvalidValueException("name boş olamaz");
        }
        return name;
    }

    private static Price requirePrice(Price price) {
        if (price == null) {
            throw new InvalidValueException("price boş olamaz");
        }
        return price;
    }

    private static int requireStock(int quantity) {
        if (quantity < 0) {
            throw new InvalidValueException("stok negatif olamaz: " + quantity);
        }
        return quantity;
    }

    private static long requireVersion(long version) {
        if (version < 1) {
            throw new InvalidValueException("version 1 veya daha büyük olmalı: " + version);
        }
        return version;
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

    public int stock() {
        return stock;
    }

    public boolean active() {
        return active;
    }

    public long detailsVersion() {
        return detailsVersion;
    }

    public long stockVersion() {
        return stockVersion;
    }
}
