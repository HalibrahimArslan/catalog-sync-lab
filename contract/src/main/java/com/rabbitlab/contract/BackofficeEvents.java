package com.rabbitlab.contract;

import java.util.Map;

/**
 * Sözleşmenin "zarf" kısmı: exchange adı, mesaj tipleri, routing key'ler ve sözleşme sürümü.
 *
 * <ul>
 *   <li>Mesaj tipi AMQP {@code type} özelliğinde taşınır (Faz 1 ile aynı).</li>
 *   <li>Routing key formatı {@code <konu>.<olay>}; Storefront {@code product.*} ve {@code stock.*} ile abone olur.</li>
 *   <li>Sözleşme sürümü {@value #SCHEMA_VERSION_HEADER} başlığında (P4-M3). Tip adı ve routing key sürümle
 *       değişmez; aynı tip adı sürüme göre farklı bir record'a okunur. Başlığı olmayan mesaj v1'dir.</li>
 * </ul>
 *
 * <p>Sürümler: v1'de fiyat iki düz alan ({@code price}, {@code currency}); v2'de tek nesne
 * ({@code price: {amount, currency}}, bkz. {@link Money}). Fiyat taşımayan mesajlar iki sürümde aynı.
 */
public final class BackofficeEvents {

    public static final String EXCHANGE = "backoffice.events";
    public static final String PRODUCT_EVENTS = "product.*";
    public static final String STOCK_EVENTS = "stock.*";
    public static final String SCHEMA_VERSION_HEADER = "x-schema-version";

    private static final Map<Integer, Map<String, Class<? extends BackofficeMessage>>> TYPES = Map.of(
            1, Map.of(
                    "ProductCreated", ProductCreatedMessage.class,
                    "ProductUpdated", ProductUpdatedMessage.class,
                    "ProductDeactivated", ProductDeactivatedMessage.class,
                    "StockUpdated", StockUpdatedMessage.class),
            2, Map.of(
                    "ProductCreated", ProductCreatedMessageV2.class,
                    "ProductUpdated", ProductUpdatedMessageV2.class,
                    "ProductDeactivated", ProductDeactivatedMessage.class,
                    "StockUpdated", StockUpdatedMessage.class));

    private BackofficeEvents() {
    }

    public static boolean isSupported(int schemaVersion) {
        return TYPES.containsKey(schemaVersion);
    }

    public static String typeOf(BackofficeMessage message) {
        return switch (message) {
            case ProductCreatedMessage ignored -> "ProductCreated";
            case ProductCreatedMessageV2 ignored -> "ProductCreated";
            case ProductUpdatedMessage ignored -> "ProductUpdated";
            case ProductUpdatedMessageV2 ignored -> "ProductUpdated";
            case ProductDeactivatedMessage ignored -> "ProductDeactivated";
            case StockUpdatedMessage ignored -> "StockUpdated";
        };
    }

    public static String routingKeyOf(BackofficeMessage message) {
        return switch (message) {
            case ProductCreatedMessage ignored -> "product.created";
            case ProductCreatedMessageV2 ignored -> "product.created";
            case ProductUpdatedMessage ignored -> "product.updated";
            case ProductUpdatedMessageV2 ignored -> "product.updated";
            case ProductDeactivatedMessage ignored -> "product.deactivated";
            case StockUpdatedMessage ignored -> "stock.updated";
        };
    }

    /** Sürümsüz (P4-M3 öncesi) mesajlar için: v1. */
    public static Class<? extends BackofficeMessage> classOf(String type) {
        return classOf(type, 1);
    }

    public static Class<? extends BackofficeMessage> classOf(String type, int schemaVersion) {
        Map<String, Class<? extends BackofficeMessage>> types = TYPES.get(schemaVersion);
        if (types == null) {
            throw new IllegalArgumentException("Desteklenmeyen sözleşme sürümü: " + schemaVersion);
        }
        Class<? extends BackofficeMessage> messageClass = type == null ? null : types.get(type);
        if (messageClass == null) {
            throw new IllegalArgumentException("Bilinmeyen mesaj tipi: " + type);
        }
        return messageClass;
    }
}
