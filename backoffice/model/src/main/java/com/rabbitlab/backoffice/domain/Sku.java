package com.rabbitlab.backoffice.domain;

/** Stok kodu: ürünün benzersiz kimliği. Hem Product'ı hem Inventory'yi tanımlar. */
public record Sku(String value) {

    public Sku {
        Require.notBlank(value, "sku");
    }

    @Override
    public String toString() {
        return value;
    }
}
