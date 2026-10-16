package com.rabbitlab.storefront.application;

import java.time.Instant;

/**
 * Bir mesajın özeti: şu anki durumu ve kaç kez denendiği.
 *
 * @param state     son denemenin sonucu
 * @param attempts  deneme sayısı (yeniden oynatmalar hariç)
 * @param lastError son hata; mesaj sonradan işlendiyse de görünür kalır (neden geciktiğini söyler)
 */
public record DeliverySummary(String messageId, String type, String sku, String queue, Integer schemaVersion,
                              DeliveryOutcome state, int attempts, String lastError, Instant firstSeen,
                              Instant lastSeen) {
}
