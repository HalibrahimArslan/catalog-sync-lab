package com.rabbitlab.storefront;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import static org.assertj.core.api.Assertions.assertThat;

/** M0 — Uygulama ayağa kalkıyor, PostgreSQL ve RabbitMQ'ya gerçekten bağlanabiliyor. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class StorefrontApplicationTest {

    @Autowired
    JdbcClient jdbc;

    @Autowired
    RabbitTemplate rabbit;

    @Test
    void should_connect_to_postgres() {
        assertThat(jdbc.sql("select 1").query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    void should_connect_to_rabbitmq() {
        Boolean open = rabbit.execute(channel -> channel.isOpen());
        assertThat(open).isTrue();
    }
}
