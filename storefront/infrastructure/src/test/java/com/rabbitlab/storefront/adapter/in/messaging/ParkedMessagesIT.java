package com.rabbitlab.storefront.adapter.in.messaging;

import com.rabbitlab.contract.BackofficeEvents;
import com.rabbitlab.contract.BackofficeMessage;
import com.rabbitlab.contract.ProductCreatedMessage;
import com.rabbitlab.contract.StockUpdatedMessage;
import com.rabbitlab.storefront.TestcontainersConfiguration;
import com.rabbitlab.storefront.application.ParkedMessage;
import com.rabbitlab.storefront.application.ParkedMessages;
import com.rabbitlab.storefront.domain.Sku;
import com.rabbitlab.storefront.domainservice.CatalogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.amqp.rabbit.support.DefaultMessagePropertiesConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * P4-M2 — Park kuyruğu yönetimi (acıtan nokta 6). Park edilen mesaj bir insanın müdahalesini bekler:
 * sorunu düzelten (ör. eksik ürünü Backoffice'te oluşturan) mesajı görebilmeli ve ana kuyruğa geri
 * gönderebilmeli.
 *
 * <pre>
 *  storefront.stock.dlq ──list──▶ (mesajlar yerinde kalır)
 *                       ──replay─▶ storefront.stock   (x-death, x-error, x-attempts silinmiş: sayaç sıfır)
 * </pre>
 *
 * Park kuyrukları {@link CatalogMessagingIT} ile ortak (aynı container'lar); testler kendi SKU'larına bakar.
 */
@SpringBootTest(properties = "storefront.messaging.retry-delay=200ms")
@Import(TestcontainersConfiguration.class)
class ParkedMessagesIT {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String STOCK = "storefront.stock";

    @Autowired
    ParkedMessages parked;

    @Autowired
    RabbitTemplate rabbit;

    @Autowired
    JsonMapper json;

    @Autowired
    CatalogRepository catalog;

    @Autowired
    RabbitListenerEndpointRegistry listeners;

    private final String sku = "SKU-" + UUID.randomUUID().toString().substring(0, 8);

    @Test
    void should_know_parking_queues_of_both_consumers() {
        assertThat(parked.queues()).containsExactlyInAnyOrder("storefront.catalog", "storefront.stock");
    }

    @Test
    void should_list_parked_messages_without_removing_them() {
        String messageId = parkStockForUnknownProduct(4);

        ParkedMessage message = mine(parked.list(STOCK, 1_000));
        assertThat(message.messageId()).isEqualTo(messageId);
        assertThat(message.type()).isEqualTo("StockUpdated");
        assertThat(message.error()).contains("bilinmeyen ürün", sku);
        assertThat(message.attempts()).isEqualTo(3);
        assertThat(message.body()).contains(sku, "\"quantity\":4");

        assertThat(mine(parked.list(STOCK, 1_000)).messageId()).as("listelemek mesajı tüketmemeli")
                .isEqualTo(messageId);
    }

    @Test
    void should_apply_replayed_message_once_the_problem_is_fixed() { // park → düzelt → yeniden oynat
        String messageId = parkStockForUnknownProduct(7);
        publish(new ProductCreatedMessage(sku, "Kupa", "Seramik kupa", new BigDecimal("100.00"), "TRY", 1));
        await().atMost(TIMEOUT).until(() -> catalog.find(new Sku(sku)).isPresent());

        assertThat(parked.replay(STOCK, messageId)).isTrue();

        await().atMost(TIMEOUT).untilAsserted(() ->
                assertThat(catalog.find(new Sku(sku)).orElseThrow().stock()).isEqualTo(7));
        assertThat(parked.list(STOCK, 1_000)).noneMatch(m -> messageId.equals(m.messageId()));
    }

    @Test
    void should_reset_attempt_counter_when_replaying() {
        String messageId = parkStockForUnknownProduct(5);
        Message original = peekRaw(messageId);
        assertThat(original.getMessageProperties().getXDeathHeader())
                .as("park edilen mesaj önceki denemelerin x-death kaydını taşıyor; sıfırlanmazsa ilk denemede tekrar park olur")
                .isNotEmpty();

        // Listener'lar dururken yeniden oynat: ana kuyruğa ne düştüğünü görelim.
        listeners.stop();
        try {
            assertThat(parked.replay(STOCK, messageId)).isTrue();

            Message replayed = receiveMine(STOCK);
            assertThat(replayed.getMessageProperties().getMessageId()).isEqualTo(messageId); // inbox aynı kimliği tanır
            assertThat(replayed.getMessageProperties().getType()).isEqualTo("StockUpdated");
            assertThat(replayed.getBody()).isEqualTo(original.getBody());
            assertThat(CatalogMessageListener.rejectedCount(replayed.getMessageProperties(), STOCK)).isZero();
            assertThat(replayed.getMessageProperties().getHeaders())
                    .doesNotContainKeys("x-death", "x-error", "x-original-queue", "x-attempts");
        } finally {
            listeners.start();
        }
    }

    @Test
    void should_answer_false_when_message_is_not_parked() {
        assertThat(parked.replay(STOCK, UUID.randomUUID().toString())).isFalse();
    }

    @Test
    void should_replay_all_and_stop_at_what_was_parked_when_it_started() {
        // Bozuk mesaj yeniden oynatılınca anında tekrar park edilir. "Kuyruk boşalana kadar" diye çalışan bir
        // replayAll onu sonsuza kadar döndürürdü; başladığı andaki mesaj sayısıyla sınırlı olmalı.
        String messageId = UUID.randomUUID().toString();
        publishRaw("{\"sku\":\"" + sku + "\",\"quantity\":-1,\"version\":1}", messageId);
        await().atMost(TIMEOUT).until(() -> isParked(messageId));

        int replayed = parked.replayAll(STOCK);

        assertThat(replayed).isPositive();
        await().atMost(TIMEOUT).until(() -> isParked(messageId)); // tekrar park edildi, döngü yok
    }

    // --- yardımcılar ---

    /** Ürünü hiç gelmeyen stok mesajı: 3 denemeden sonra park edilir. */
    private String parkStockForUnknownProduct(int quantity) {
        String messageId = UUID.randomUUID().toString();
        publish(new StockUpdatedMessage(sku, quantity, 1), messageId);
        await().atMost(TIMEOUT).until(() -> isParked(messageId));
        return messageId;
    }

    private boolean isParked(String messageId) {
        return parked.list(STOCK, 1_000).stream().anyMatch(m -> messageId.equals(m.messageId()));
    }

    private ParkedMessage mine(List<ParkedMessage> messages) {
        return messages.stream().filter(m -> m.body().contains(sku)).findFirst().orElseThrow();
    }

    /** Park kuyruğundaki ham mesajı, yerinde bırakarak okur (başlıklara bakmak için). */
    private Message peekRaw(String messageId) {
        return rabbit.execute(channel -> {
            try {
                var response = channel.basicGet(StorefrontTopology.parkingQueueOf(STOCK), false);
                while (response != null && !messageId.equals(response.getProps().getMessageId())) {
                    response = channel.basicGet(StorefrontTopology.parkingQueueOf(STOCK), false);
                }
                var properties = new DefaultMessagePropertiesConverter()
                        .toMessageProperties(response.getProps(), response.getEnvelope(), UTF_8.name());
                return new Message(response.getBody(), properties);
            } finally {
                channel.basicNack(0, true, true);
            }
        });
    }

    private Message receiveMine(String queue) {
        Message message;
        long deadline = System.currentTimeMillis() + TIMEOUT.toMillis();
        while (System.currentTimeMillis() < deadline) {
            message = rabbit.receive(queue, 500);
            if (message != null && new String(message.getBody(), UTF_8).contains(sku)) {
                return message;
            }
        }
        throw new AssertionError("Yeniden oynatılan mesaj " + queue + " kuyruğuna gelmedi");
    }

    private void publish(BackofficeMessage message) {
        publish(message, UUID.randomUUID().toString());
    }

    private void publish(BackofficeMessage message, String messageId) {
        rabbit.send(BackofficeEvents.EXCHANGE, BackofficeEvents.routingKeyOf(message), MessageBuilder
                .withBody(json.writeValueAsBytes(message))
                .setContentType("application/json")
                .setType(BackofficeEvents.typeOf(message))
                .setMessageId(messageId)
                .build());
    }

    private void publishRaw(String body, String messageId) {
        rabbit.send(BackofficeEvents.EXCHANGE, "stock.updated", MessageBuilder.withBody(body.getBytes(UTF_8))
                .setContentType("application/json").setType("StockUpdated").setMessageId(messageId).build());
    }
}
