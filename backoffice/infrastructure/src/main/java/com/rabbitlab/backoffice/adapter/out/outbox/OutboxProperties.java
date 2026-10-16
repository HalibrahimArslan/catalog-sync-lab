package com.rabbitlab.backoffice.adapter.out.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param schedulingEnabled relay arka planda kendiliğinden dönsün mü (testlerde elle çağırmak için kapatılır)
 * @param pollInterval      iki relay turu arasındaki bekleme
 * @param batchSize         bir turda en fazla kaç mesaj
 * @param confirmTimeout    broker onayını en fazla ne kadar bekleyelim
 * @param cleanupEnabled    yayınlanmış satırların temizliği açık mı
 * @param cleanupInterval   iki temizlik turu arasındaki bekleme
 * @param retention         yayınlanmış satır silinmeden önce ne kadar tutulsun (hata ayıklamak için)
 */
@ConfigurationProperties("backoffice.outbox")
public record OutboxProperties(
        @DefaultValue("true") boolean schedulingEnabled,
        @DefaultValue("500ms") Duration pollInterval,
        @DefaultValue("100") int batchSize,
        @DefaultValue("5s") Duration confirmTimeout,
        @DefaultValue("true") boolean cleanupEnabled,
        @DefaultValue("1h") Duration cleanupInterval,
        @DefaultValue("7d") Duration retention) {
}
