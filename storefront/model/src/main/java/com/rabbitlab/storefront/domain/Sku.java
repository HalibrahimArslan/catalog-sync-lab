package com.rabbitlab.storefront.domain;

/** Stok kodu. Backoffice'teki {@code Sku} ile aynı kavram, ayrı sınıf: context'ler model paylaşmaz. */
public record Sku(String value) {

    public Sku {
        if (value == null || value.isBlank()) {
            throw new InvalidValueException("sku boş olamaz");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
