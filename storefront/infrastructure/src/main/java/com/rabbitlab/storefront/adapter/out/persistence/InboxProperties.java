package com.rabbitlab.storefront.adapter.out.persistence;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param cleanupEnabled  eski inbox kayıtlarının temizliği açık mı
 * @param cleanupInterval iki temizlik turu arasındaki bekleme
 * @param retention       işlenmiş kimlik ne kadar hatırlansın. Bir mesajın makul olarak tekrar
 *                        gelebileceği en uzun süreden uzun olmalı; daha geç gelen tekrarı versiyon kontrolü yakalar.
 */
@ConfigurationProperties("storefront.inbox")
public record InboxProperties(
        @DefaultValue("true") boolean cleanupEnabled,
        @DefaultValue("1h") Duration cleanupInterval,
        @DefaultValue("7d") Duration retention) {
}
