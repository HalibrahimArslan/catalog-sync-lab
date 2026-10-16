package com.rabbitlab.storefront;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

/**
 * Testlerin gerçek PostgreSQL ve RabbitMQ'ya karşı koşması için container'lar.
 *
 * <p>{@code @ServiceConnection}: Spring, bağlantı ayarlarını (host, port, kullanıcı) container'dan
 * kendisi okur. Faz 1'deki elle kurulan {@code ConnectionFactory}'nin yerini alır.
 * Spring test context'i önbelleğe alındığı için container'lar test sınıfları arasında paylaşılır.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:17-alpine");
    }

    @Bean
    @ServiceConnection
    RabbitMQContainer rabbit() {
        return new RabbitMQContainer("rabbitmq:4.1-management");
    }
}
