package com.rabbitlab.backoffice.adapter.out.outbox;

import com.rabbitlab.contract.BackofficeEvents;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param schedulingEnabled relay arka planda kendiliğinden dönsün mü (testlerde elle çağırmak için kapatılır)
 * @param pollInterval      yedek polling aralığı; asıl tetik NOTIFY (P4-M1)
 * @param notifyEnabled     outbox'a satır eklenince relay anında uyansın mı (LISTEN/NOTIFY)
 * @param batchSize         bir turda en fazla kaç mesaj
 * @param confirmTimeout    broker onayını en fazla ne kadar bekleyelim
 * @param cleanupEnabled    yayınlanmış satırların temizliği açık mı
 * @param cleanupInterval   iki temizlik turu arasındaki bekleme
 * @param retention         yayınlanmış satır silinmeden önce ne kadar tutulsun (hata ayıklamak için)
 * @param schemaVersion     outbox'a yazılan mesajların sözleşme sürümü (P4-M3). Varsayılan 1: Storefront iki
 *                          sürümü okuyana kadar Backoffice eskiyi yayınlar; geçiş bu ayarı 2 yapmaktır
 */
@ConfigurationProperties("backoffice.outbox")
public record OutboxProperties(
        @DefaultValue("true") boolean schedulingEnabled,
        @DefaultValue("5s") Duration pollInterval,
        @DefaultValue("true") boolean notifyEnabled,
        @DefaultValue("100") int batchSize,
        @DefaultValue("5s") Duration confirmTimeout,
        @DefaultValue("true") boolean cleanupEnabled,
        @DefaultValue("1h") Duration cleanupInterval,
        @DefaultValue("7d") Duration retention,
        @DefaultValue("1") int schemaVersion) {

    public OutboxProperties {
        // Uygulama açılırken patlasın; ilk ürün kaydedilirken, kullanıcının isteğinde değil.
        if (!BackofficeEvents.isSupported(schemaVersion)) {
            throw new IllegalArgumentException("Desteklenmeyen sözleşme sürümü: " + schemaVersion);
        }
    }
}
