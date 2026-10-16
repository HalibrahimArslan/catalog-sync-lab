package com.rabbitlab.storefront.application;

/** Teslimat geçmişi araması. {@code null} alanlar filtre değildir; {@code state} son duruma göre. En yeni önce. */
public record DeliveryQuery(String sku, String messageId, DeliveryOutcome state, int limit) {
}
