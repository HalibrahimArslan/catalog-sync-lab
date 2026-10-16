package com.rabbitlab.backoffice.application;

/** Outbox araması. {@code null} alanlar filtre değildir. Sonuçlar en yeni önce. */
public record OutboxQuery(String sku, String messageId, OutboxState state, int limit) {
}
