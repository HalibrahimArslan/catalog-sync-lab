package com.rabbitlab.storefront.adapter.in.messaging;

import com.rabbitlab.storefront.application.CatalogService;
import com.rabbitlab.storefront.application.CatalogUpdate;
import com.rabbitlab.storefront.application.DeliveryAttempt;
import com.rabbitlab.storefront.application.DeliveryLog;
import com.rabbitlab.storefront.application.DeliveryOutcome;
import com.rabbitlab.storefront.domain.InvalidValueException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Storefront'un RabbitMQ girişi (dış halka). Mesajı çevirir ve application servisini çağırır;
 * RabbitMQ'ya özgü her şey (ack, x-death, park) burada kalır. Transaction ve inbox bilmez (P3-M2):
 * ikisi de {@link CatalogService}'in içinde.
 *
 * <table>
 *   <tr><th>Durum</th><th>Tepki</th></tr>
 *   <tr><td>Başarılı</td><td>ack</td></tr>
 *   <tr><td>Daha önce işlenmiş message-id</td><td>hiçbir şey yapmadan ack</td></tr>
 *   <tr><td>Bozuk/kurallara aykırı/kimliksiz mesaj</td><td>park kuyruğuna taşı, ack (tekrar denemek işe yaramaz)</td></tr>
 *   <tr><td>Geçici hata (bilinmeyen ürün, DB kopuk...)</td>
 *       <td>reject → bekleme odası → gecikmeli tekrar; {@code max-attempts} sonra park</td></tr>
 * </table>
 * Faz 1'deki iki modlu consumer ({@code parkingQueue == null}) yok: tek politika var (acıtan nokta 5).
 *
 * <p>Mesaj takibi: her denemenin sonucu ve hatası teslimat geçmişine ({@link DeliveryLog}) yazılır.
 * Yazılamazsa sadece log'a düşer; gözlem, işlemeyi bozmamalı.
 */
@Component
class CatalogMessageListener {

    private static final Logger log = LoggerFactory.getLogger(CatalogMessageListener.class);
    private static final long PARK_CONFIRM_TIMEOUT_MS = 5_000;

    private final MessageTranslator translator;
    private final CatalogService catalog;
    private final RabbitTemplate rabbit;
    private final MessagingProperties properties;
    private final DeliveryLog deliveries;

    CatalogMessageListener(MessageTranslator translator, CatalogService catalog, RabbitTemplate rabbit,
                           MessagingProperties properties, DeliveryLog deliveries) {
        this.translator = translator;
        this.catalog = catalog;
        this.rabbit = rabbit;
        this.properties = properties;
        this.deliveries = deliveries;
    }

    @RabbitListener(queues = StorefrontTopology.CATALOG_QUEUE)
    void onCatalogMessage(Message message) {
        handle(message, StorefrontTopology.CATALOG_QUEUE);
    }

    @RabbitListener(queues = StorefrontTopology.STOCK_QUEUE)
    void onStockMessage(Message message) {
        handle(message, StorefrontTopology.STOCK_QUEUE);
    }

    private void handle(Message message, String queue) {
        long attempt = rejectedCount(message.getMessageProperties(), queue) + 1;
        try {
            CatalogUpdate update = translator.translate(message);
            boolean applied = catalog.apply(message.getMessageProperties().getMessageId(), update);
            record(message, queue, attempt, applied ? DeliveryOutcome.PROCESSED : DeliveryOutcome.DUPLICATE, null);
        } catch (InvalidMessageException | InvalidValueException e) {
            park(message, queue, attempt, e);
            record(message, queue, attempt, DeliveryOutcome.PARKED, e);
        } catch (RuntimeException e) {
            if (attempt >= properties.maxAttempts()) {
                park(message, queue, attempt, e);
                record(message, queue, attempt, DeliveryOutcome.PARKED, e);
            } else {
                log.info("Geçici hata, {}. deneme başarısız; bekleme odasına: {}", attempt, e.getMessage());
                record(message, queue, attempt, DeliveryOutcome.RETRY, e);
                throw new AmqpRejectAndDontRequeueException(e); // DLX → <kuyruk>.retry
            }
        }
    }

    /** Teslimat geçmişine yazar; yazamazsa işlemeyi etkilemeden log'a düşer. */
    private void record(Message message, String queue, long attempt, DeliveryOutcome outcome, RuntimeException cause) {
        MessageProperties props = message.getMessageProperties();
        try {
            deliveries.record(new DeliveryAttempt(props.getMessageId(), queue, props.getType(),
                    translator.skuOf(message.getBody()), MessageTranslator.readSchemaVersion(message), (int) attempt,
                    outcome, cause == null ? null : errorOf(cause)));
        } catch (RuntimeException e) {
            log.warn("Teslimat geçmişine yazılamadı ({} {}): {}", props.getMessageId(), outcome, e.getMessage());
        }
    }

    /** Park başlığındaki ({@code x-error}) ve teslimat geçmişindeki hata metni aynı. */
    static String errorOf(RuntimeException cause) {
        return cause.getClass().getSimpleName() + ": " + cause.getMessage();
    }

    /** Kaçıncı deneme? x-death'te bu kuyruktan "rejected" sebebiyle kaç kez çıktığına bakar. */
    static long rejectedCount(MessageProperties properties, String queue) {
        List<Map<String, ?>> deaths = properties.getXDeathHeader();
        if (deaths == null) {
            return 0;
        }
        return deaths.stream()
                .filter(death -> queue.equals(String.valueOf(death.get("queue")))
                        && "rejected".equals(String.valueOf(death.get("reason"))))
                .map(death -> death.get("count"))
                .filter(Number.class::isInstance)
                .mapToLong(count -> ((Number) count).longValue())
                .findFirst()
                .orElse(0);
    }

    /**
     * Mesajı hata bilgisiyle park kuyruğuna kopyalar ve onayını bekler. Listener normal döndüğünde
     * orijinal mesaj ack'lenir. Onay gelmezse exception fırlar, orijinal reject edilir; mesaj kaybolmaz.
     */
    private void park(Message message, String queue, long attempts, RuntimeException cause) {
        log.warn("Mesaj park ediliyor ({} deneme): {}", attempts, cause.getMessage());
        MessageProperties props = message.getMessageProperties();
        props.setHeader("x-error", errorOf(cause));
        props.setHeader("x-original-queue", queue);
        props.setHeader("x-attempts", attempts);
        rabbit.invoke(operations -> {
            operations.send("", StorefrontTopology.parkingQueueOf(queue), message);
            operations.waitForConfirmsOrDie(PARK_CONFIRM_TIMEOUT_MS);
            return null;
        });
    }
}
