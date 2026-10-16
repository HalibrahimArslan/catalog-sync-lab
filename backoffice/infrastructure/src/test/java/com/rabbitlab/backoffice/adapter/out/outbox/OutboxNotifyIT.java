package com.rabbitlab.backoffice.adapter.out.outbox;

import com.rabbitlab.backoffice.TestcontainersConfiguration;
import com.rabbitlab.backoffice.application.CreateProductCommand;
import com.rabbitlab.backoffice.application.ProductService;
import com.rabbitlab.backoffice.domain.Sku;
import com.rabbitlab.backoffice.domain.product.Price;
import com.rabbitlab.contract.BackofficeEvents;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P4-M1 — Relay bildirimle uyanır. Polling aralığı bilerek 1 dakika: mesaj birkaç saniye içinde
 * geliyorsa onu polling değil, outbox'a satır eklenince gönderilen {@code NOTIFY} getirmiştir.
 *
 * <pre>
 *  ProductService ──insert──▶ outbox ──trigger: pg_notify('outbox')──▶ (commit) ──▶ relay uyanır ──▶ RabbitMQ
 * </pre>
 * Bildirim transaction commit olunca gider: relay, henüz commit olmamış satırı aramaya kalkmaz.
 */
@SpringBootTest(properties = {
        "backoffice.outbox.poll-interval=1m",
        "backoffice.outbox.cleanup-enabled=false"})
@Import(TestcontainersConfiguration.class)
class OutboxNotifyIT {

    @Autowired
    ProductService productService;

    @Autowired
    AmqpAdmin admin;

    @Autowired
    RabbitTemplate rabbit;

    @Autowired
    JdbcClient jdbc;

    private final Queue queue = new Queue("test." + UUID.randomUUID(), true, false, false);

    @AfterEach
    void deleteQueue() {
        admin.deleteQueue(queue.getName());
    }

    @Test
    void should_publish_within_seconds_although_poll_interval_is_one_minute() throws Exception {
        admin.declareQueue(queue);
        admin.declareBinding(BindingBuilder.bind(queue).to(new TopicExchange(BackofficeEvents.EXCHANGE)).with("#"));
        Thread.sleep(500); // uygulama açılışındaki ilk polling turu geçsin; bundan sonra 1 dakika polling yok

        for (int i = 0; i < 3; i++) { // birkaç kez: her biri ayrı bir bildirim
            Sku sku = new Sku("SKU-" + UUID.randomUUID().toString().substring(0, 8));
            productService.create(new CreateProductCommand(sku, "Kupa", null, new Price(new BigDecimal("100"), "TRY")));

            Message message = rabbit.receive(queue.getName(), 3_000);
            assertThat(message).as("%d. mesaj bildirimle gelmeliydi", i + 1).isNotNull();
            assertThat(message.getBody()).asString().contains(sku.value());
        }
    }

    @Test
    void should_have_trigger_that_notifies_on_insert() {
        // Mekanizmanın veritabanı tarafı: outbox'ta bir trigger var ve 'outbox' kanalına bildirim gönderiyor.
        assertThat(jdbc.sql("""
                        select count(*) from information_schema.triggers
                        where event_object_table = 'outbox' and event_manipulation = 'INSERT'""")
                .query(Integer.class).single()).isEqualTo(1);
    }
}
