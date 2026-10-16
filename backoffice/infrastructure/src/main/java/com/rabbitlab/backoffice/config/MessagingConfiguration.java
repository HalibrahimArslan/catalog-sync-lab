package com.rabbitlab.backoffice.config;

import com.rabbitlab.backoffice.adapter.out.outbox.OutboxProperties;
import com.rabbitlab.contract.BackofficeEvents;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Backoffice sadece exchange'i kurar; kuyrukları bilmez. Kuyrukları dinleyen taraf (Storefront)
 * kurar ve bağlar (Faz 1 M3: publisher kuyrukları bilmez).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OutboxProperties.class)
class MessagingConfiguration {

    @Bean
    TopicExchange backofficeEvents() {
        return new TopicExchange(BackofficeEvents.EXCHANGE, true, false);
    }
}
