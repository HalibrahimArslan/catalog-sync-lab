package com.rabbitlab.backoffice.application;

import java.time.Instant;

/**
 * Outbox'taki bir mesaj, takip eden insana gösterilen hâliyle. {@code messageId} AMQP message-id'si:
 * Storefront'un teslimat geçmişinde de aynı kimlikle görünür.
 */
public record OutboxEntry(String messageId, String sku, String type, String routingKey, int schemaVersion,
                          String payload, Instant createdAt, Instant publishedAt, int attempts, String lastError,
                          OutboxState state) {
}
