package com.rabbitlab.storefront.adapter.in.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param retryDelay  geçici hatadan sonra mesajın bekleme odasında kalma süresi
 * @param maxAttempts geçici hatada toplam deneme sayısı; aşılınca mesaj park edilir
 */
@ConfigurationProperties("storefront.messaging")
public record MessagingProperties(
        @DefaultValue("5s") Duration retryDelay,
        @DefaultValue("3") int maxAttempts) {
}
