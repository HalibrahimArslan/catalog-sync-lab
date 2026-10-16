package com.rabbitlab.storefront.application;

import com.rabbitlab.storefront.domain.InvalidValueException;
import com.rabbitlab.storefront.domain.Price;
import com.rabbitlab.storefront.domain.Sku;

/**
 * Kataloğa yansıtılacak bir değişiklik, Storefront'un kendi dilinde.
 *
 * <p>Mesaj sözleşmesindeki {@code ProductDeactivatedMessage} burada {@link Withdrawn} ("vitrinden
 * kaldırıldı"); çeviriyi messaging adapter'ı yapar. Sözleşme değişirse sadece adapter değişir.
 */
public sealed interface CatalogUpdate {

    Sku sku();

    record NewProduct(Sku sku, String name, String description, Price price, long version)
            implements CatalogUpdate {
        public NewProduct {
            requireDetails(sku, name, price, version);
        }
    }

    record DetailsChanged(Sku sku, String name, String description, Price price, long version)
            implements CatalogUpdate {
        public DetailsChanged {
            requireDetails(sku, name, price, version);
        }
    }

    record StockChanged(Sku sku, int quantity, long version) implements CatalogUpdate {
        public StockChanged {
            require(sku != null, "sku boş olamaz");
            require(quantity >= 0, "stok negatif olamaz: " + quantity);
            require(version >= 1, "version 1 veya daha büyük olmalı: " + version);
        }
    }

    record Withdrawn(Sku sku, long version) implements CatalogUpdate {
        public Withdrawn {
            require(sku != null, "sku boş olamaz");
            require(version >= 1, "version 1 veya daha büyük olmalı: " + version);
        }
    }

    private static void requireDetails(Sku sku, String name, Price price, long version) {
        require(sku != null, "sku boş olamaz");
        require(name != null && !name.isBlank(), "name boş olamaz");
        require(price != null, "price boş olamaz");
        require(version >= 1, "version 1 veya daha büyük olmalı: " + version);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new InvalidValueException(message);
        }
    }
}
