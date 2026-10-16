package com.rabbitlab.backoffice.adapter.out.outbox;

import com.rabbitlab.backoffice.application.ProductService;
import com.rabbitlab.backoffice.TestcontainersConfiguration;
import com.rabbitlab.backoffice.application.CreateProductCommand;
import com.rabbitlab.backoffice.domain.Sku;
import com.rabbitlab.backoffice.domain.product.Price;
import com.rabbitlab.contract.BackofficeEvents;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P2-M3 — Uygulama çalışırken relay arka planda kendiliğinden döner: use case çağrısından
 * birkaç yüz milisaniye sonra mesaj kuyrukta.
 */
@SpringBootTest(properties = "backoffice.outbox.poll-interval=100ms")
@Import(TestcontainersConfiguration.class)
class OutboxRelaySchedulingIT {

    @Autowired
    ProductService productService;

    @Autowired
    AmqpAdmin admin;

    @Autowired
    RabbitTemplate rabbit;

    @Test
    void should_deliver_persistent_message_shortly_after_use_case_returns() {
        // AnonymousQueue değil: o auto-delete; ilk receive() consumer'ı kapatınca kuyruk silinir.
        Queue queue = new Queue("test." + UUID.randomUUID(), true, false, false);
        admin.declareQueue(queue);
        admin.declareBinding(BindingBuilder.bind(queue).to(new TopicExchange(BackofficeEvents.EXCHANGE)).with("#"));
        Sku sku = new Sku("SKU-" + UUID.randomUUID().toString().substring(0, 8));

        try {
            productService.create(new CreateProductCommand(sku, "Kupa", "Seramik kupa",
                    new Price(new BigDecimal("100"), "TRY")));

            Message message = rabbit.receive(queue.getName(), 5_000);
            assertThat(message).isNotNull();
            assertThat(message.getMessageProperties().getType()).isEqualTo("ProductCreated");
            // Persistent: broker restart'ta kaybolmaz (Faz 1 M4).
            assertThat(message.getMessageProperties().getReceivedDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        } finally {
            admin.deleteQueue(queue.getName());
        }
    }
}
