package com.rabbitlab.backoffice.domain;

/** Domain nesnelerinin kendi kendini doğrulaması için küçük yardımcılar. */
public final class Require {

    private Require() {
    }

    public static String notBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new InvalidValueException(field + " boş olamaz");
        }
        return value;
    }

    public static <T> T notNull(T value, String field) {
        if (value == null) {
            throw new InvalidValueException(field + " boş olamaz");
        }
        return value;
    }
}
