package com.rabbitlab.backoffice.application;

import java.util.List;
import java.util.Map;

/**
 * Outbox'ın okuma tarafı: mesaj takibi için. Uygulaması outbox adapter'ında, doğrudan SQL ile.
 *
 * <p>Neden bu halkada? Web ve outbox iki ayrı adapter, birbirini tanımaz (Onion kuralı). Yönetim uç
 * noktası outbox tablosuna ancak içteki bir arayüz üzerinden ulaşır. Orkestre edilecek akış yok; araya
 * servis konmadı (Storefront'taki {@code CatalogQueries} ile aynı karar).
 */
public interface OutboxQueries {

    List<OutboxEntry> find(OutboxQuery query);

    /** Yalnızca sayısı sıfırdan büyük durumlar. */
    Map<OutboxState, Long> countByState();
}
