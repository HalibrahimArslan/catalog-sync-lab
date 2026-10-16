package com.rabbitlab.storefront.adapter.in.messaging;

import com.rabbitlab.storefront.application.ParkedMessage;
import com.rabbitlab.storefront.application.ParkedMessages;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.GetResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Park kuyruklarına (P4-M2) doğrudan AMQP kanalıyla erişir. RabbitMQ'da "kuyruğa bak ama alma"
 * işlemi yok; mesajlar ack'lenmeden alınır ({@code basicGet}, autoAck kapalı) ve iş bitince
 * {@code basicNack(0, multiple, requeue)} ile hepsi geri bırakılır. RabbitMQ geri bırakılan mesajı
 * eski yerine koyar, sıra bozulmaz. Park kuyruğunu kimse dinlemediği için bu arada başkası görmez.
 *
 * <p>Yeniden oynatma: mesaj ana kuyruğa (default exchange, doğrudan kuyruk adıyla) yayınlanır,
 * broker onayı beklenir, <b>sonra</b> park kuyruğundaki kopya ack'lenir. Arada çökersek mesaj iki
 * yerde birden olur; ana kuyruktaki kopya aynı message-id'yi taşıdığı için inbox ikinci işlemeyi
 * ayıklar (at-least-once, Outbox relay ile aynı anlaşma). Topic exchange'e değil kuyruğa
 * gönderiyoruz: aynı event'i dinleyen başka bir tüketici varsa o mesajı ikinci kez almamalı.
 *
 * <p>Sayaç sıfırlama: listener deneme sayısını {@code x-death}'ten okur ({@link CatalogMessageListener#rejectedCount});
 * park sırasında bu başlık mesajla birlikte kopyalanıyor. Silinmezse yeniden oynatılan mesaj ilk
 * denemede tekrar park edilirdi. Park bilgisi başlıkları ({@code x-error} vb.) da silinir: mesaj
 * tekrar park edilirse yenileri yazılır.
 */
@Component
class RabbitParkedMessages implements ParkedMessages {

    private static final Logger log = LoggerFactory.getLogger(RabbitParkedMessages.class);
    private static final long CONFIRM_TIMEOUT_MS = 5_000;
    private static final List<String> QUEUES = List.of(StorefrontTopology.CATALOG_QUEUE, StorefrontTopology.STOCK_QUEUE);
    private static final Set<String> PARKING_HEADERS = Set.of("x-error", "x-original-queue", "x-attempts");

    private final RabbitTemplate rabbit;

    RabbitParkedMessages(RabbitTemplate rabbit) {
        this.rabbit = rabbit;
    }

    @Override
    public List<String> queues() {
        return QUEUES;
    }

    @Override
    public List<ParkedMessage> list(String queue, int limit) {
        String parking = parkingQueueOf(queue);
        return rabbit.execute(channel -> {
            try {
                List<ParkedMessage> messages = new ArrayList<>();
                GetResponse response;
                while (messages.size() < limit && (response = channel.basicGet(parking, false)) != null) {
                    messages.add(view(response));
                }
                return messages;
            } finally {
                releaseUnacked(channel);
            }
        });
    }

    @Override
    public boolean replay(String queue, String messageId) {
        String parking = parkingQueueOf(queue);
        Boolean replayed = rabbit.execute(channel -> {
            try {
                long remaining = channel.messageCount(parking);
                GetResponse response;
                while (remaining-- > 0 && (response = channel.basicGet(parking, false)) != null) {
                    if (messageId.equals(response.getProps().getMessageId())) {
                        republish(channel, queue, response);
                        channel.waitForConfirmsOrDie(CONFIRM_TIMEOUT_MS);
                        channel.basicAck(response.getEnvelope().getDeliveryTag(), false);
                        return true;
                    }
                }
                return false;
            } finally {
                releaseUnacked(channel);
            }
        });
        if (Boolean.TRUE.equals(replayed)) {
            log.info("Park edilen mesaj yeniden oynatıldı: {} → {}", messageId, queue);
        }
        return Boolean.TRUE.equals(replayed);
    }

    @Override
    public int replayAll(String queue) {
        String parking = parkingQueueOf(queue);
        Integer replayed = rabbit.execute(channel -> {
            try {
                // Başlangıçtaki sayı sınır: bozuk mesaj anında tekrar park edilir, "boşalana kadar" sonsuz döner.
                long parked = channel.messageCount(parking);
                int count = 0;
                long lastTag = 0;
                GetResponse response;
                while (count < parked && (response = channel.basicGet(parking, false)) != null) {
                    republish(channel, queue, response);
                    lastTag = response.getEnvelope().getDeliveryTag();
                    count++;
                }
                if (count > 0) {
                    channel.waitForConfirmsOrDie(CONFIRM_TIMEOUT_MS);
                    channel.basicAck(lastTag, true);
                }
                return count;
            } finally {
                releaseUnacked(channel);
            }
        });
        log.info("{} kuyruğunda park edilen {} mesaj yeniden oynatıldı", queue, replayed);
        return replayed == null ? 0 : replayed;
    }

    private static String parkingQueueOf(String queue) {
        if (!QUEUES.contains(queue)) {
            throw new IllegalArgumentException("Park kuyruğu olmayan kuyruk: " + queue);
        }
        return StorefrontTopology.parkingQueueOf(queue);
    }

    /** Ack'lenmemiş ne kaldıysa park kuyruğuna, eski yerine geri bırakır (tag 0 + multiple: hepsi). */
    private static void releaseUnacked(Channel channel) throws IOException {
        channel.basicNack(0, true, true);
    }

    private static void republish(Channel channel, String queue, GetResponse parked) throws IOException {
        AMQP.BasicProperties properties = parked.getProps();
        Map<String, Object> headers = new HashMap<>(properties.getHeaders() == null ? Map.of() : properties.getHeaders());
        headers.keySet().removeIf(RabbitParkedMessages::isRetryOrParkingHeader);
        channel.basicPublish("", queue, properties.builder().headers(headers).build(), parked.getBody());
    }

    /** {@code x-death} ve RabbitMQ'nun özet başlıkları ({@code x-first-death-*}, {@code x-last-death-*}) + park bilgisi. */
    private static boolean isRetryOrParkingHeader(String name) {
        return name.equals("x-death") || name.startsWith("x-first-death-") || name.startsWith("x-last-death-")
                || PARKING_HEADERS.contains(name);
    }

    private static ParkedMessage view(GetResponse response) {
        AMQP.BasicProperties properties = response.getProps();
        Map<String, Object> headers = properties.getHeaders() == null ? Map.of() : properties.getHeaders();
        Object error = headers.get("x-error");
        long attempts = headers.get("x-attempts") instanceof Number number ? number.longValue() : 0;
        return new ParkedMessage(properties.getMessageId(), properties.getType(),
                error == null ? null : error.toString(), attempts, new String(response.getBody(), UTF_8));
    }
}
