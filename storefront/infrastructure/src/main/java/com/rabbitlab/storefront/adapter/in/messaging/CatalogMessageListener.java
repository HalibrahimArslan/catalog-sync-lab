package com.rabbitlab.storefront.adapter.in.messaging;

import com.rabbitlab.storefront.application.CatalogService;
import com.rabbitlab.storefront.application.CatalogUpdate;
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
 */
@Component
class CatalogMessageListener {

    private static final Logger log = LoggerFactory.getLogger(CatalogMessageListener.class);
    private static final long PARK_CONFIRM_TIMEOUT_MS = 5_000;

    private final MessageTranslator translator;
    private final CatalogService catalog;
    private final RabbitTemplate rabbit;
    private final MessagingProperties properties;

    CatalogMessageListener(MessageTranslator translator, CatalogService catalog, RabbitTemplate rabbit,
                           MessagingProperties properties) {
        this.translator = translator;
        this.catalog = catalog;
        this.rabbit = rabbit;
        this.properties = properties;
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
            catalog.apply(message.getMessageProperties().getMessageId(), update);
        } catch (InvalidMessageException | InvalidValueException e) {
            park(message, queue, attempt, e);
        } catch (RuntimeException e) {
            if (attempt >= properties.maxAttempts()) {
                park(message, queue, attempt, e);
            } else {
                log.info("Geçici hata, {}. deneme başarısız; bekleme odasına: {}", attempt, e.getMessage());
                throw new AmqpRejectAndDontRequeueException(e); // DLX → <kuyruk>.retry
            }
        }
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
        props.setHeader("x-error", cause.getClass().getSimpleName() + ": " + cause.getMessage());
        props.setHeader("x-original-queue", queue);
        props.setHeader("x-attempts", attempts);
        rabbit.invoke(operations -> {
            operations.send("", StorefrontTopology.parkingQueueOf(queue), message);
            operations.waitForConfirmsOrDie(PARK_CONFIRM_TIMEOUT_MS);
            return null;
        });
    }
}
