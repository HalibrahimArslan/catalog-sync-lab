package com.rabbitlab.storefront.application;

import java.util.List;
import java.util.Map;

/**
 * Teslimat geçmişi: her mesajın her denemesi, sonucu ve hatası (mesaj takibi). Celery'nin "result
 * backend"inin karşılığı: RabbitMQ işlenen mesajı unutur, tekrar denenen mesajın neden başarısız
 * olduğunu da hiçbir yere yazmaz. Uygulaması en dış halkada, PostgreSQL'de.
 *
 * <p>Neden bu halkada? Yazan messaging adapter'ı, okuyan web adapter'ı; ikisi birbirini tanımaz
 * ({@link ParkedMessages} ile aynı sebep).
 *
 * <p>Gözlem amaçlı: kayıt yazılamazsa mesajın işlenmesi etkilenmemeli (çağıran tarafın sorumluluğu).
 */
public interface DeliveryLog {

    void record(DeliveryAttempt attempt);

    List<DeliverySummary> find(DeliveryQuery query);

    /** Bir mesajın tüm denemeleri, sırayla; hiç görülmediyse boş. */
    List<DeliveryEntry> history(String messageId);

    /** Mesaj sayıları, son durumlarına göre. Yalnızca sıfırdan büyükler. */
    Map<DeliveryOutcome, Long> countByState();
}
