package com.rabbitlab.storefront.adapter.in.messaging;

import com.rabbitlab.contract.BackofficeEvents;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Storefront'un RabbitMQ haritası (Faz 1'deki {@code Topology}'nin Spring karşılığı). Kuyrukları
 * dinleyen taraf kurar; Backoffice sadece exchange'i bilir.
 *
 * <p>Her tüketici kuyruğu için:
 * <ul>
 *   <li>{@code <kuyruk>}: reject edilen mesaj DLX ile {@code <kuyruk>.retry}'a gider.</li>
 *   <li>{@code <kuyruk>.retry}: bekleme odası. Kimse okumaz; TTL dolunca mesaj ana kuyruğa döner.</li>
 *   <li>{@code <kuyruk>.dlq}: park kuyruğu. Artık denenmeyecek mesajlar; bir insan bakmalı.</li>
 * </ul>
 * Spring bu {@code Declarable}'ları bağlantı açılınca kendisi declare eder (RabbitAdmin).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MessagingProperties.class)
class StorefrontTopology {

    static final String CATALOG_QUEUE = "storefront.catalog";
    static final String STOCK_QUEUE = "storefront.stock";

    static String retryQueueOf(String queue) {
        return queue + ".retry";
    }

    static String parkingQueueOf(String queue) {
        return queue + ".dlq";
    }

    @Bean
    Declarables storefrontQueues(MessagingProperties properties) {
        // Backoffice de aynı ayarlarla declare ediyor; declare idempotent, kim önce açılırsa kurar.
        TopicExchange exchange = new TopicExchange(BackofficeEvents.EXCHANGE, true, false);
        List<Declarable> declarables = new ArrayList<>(List.of(exchange));
        declarables.addAll(consumerQueue(exchange, CATALOG_QUEUE, BackofficeEvents.PRODUCT_EVENTS, properties));
        declarables.addAll(consumerQueue(exchange, STOCK_QUEUE, BackofficeEvents.STOCK_EVENTS, properties));
        return new Declarables(declarables);
    }

    private static List<Declarable> consumerQueue(TopicExchange exchange, String name, String bindingKey,
                                                  MessagingProperties properties) {
        Queue queue = QueueBuilder.durable(name)
                .deadLetterExchange("")
                .deadLetterRoutingKey(retryQueueOf(name))
                .build();
        Queue retry = QueueBuilder.durable(retryQueueOf(name))
                .ttl((int) properties.retryDelay().toMillis())
                .deadLetterExchange("")
                .deadLetterRoutingKey(name)
                .build();
        Queue parking = QueueBuilder.durable(parkingQueueOf(name)).build();
        Binding binding = BindingBuilder.bind(queue).to(exchange).with(bindingKey);
        return List.of(queue, retry, parking, binding);
    }
}
