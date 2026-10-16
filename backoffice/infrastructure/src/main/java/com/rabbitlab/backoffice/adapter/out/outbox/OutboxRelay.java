package com.rabbitlab.backoffice.adapter.out.outbox;

import com.rabbitlab.contract.BackofficeEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Outbox → RabbitMQ. Bir tur:
 * <ol>
 *   <li>Yayınlanmamış satırları kilitleyerek oku ({@code FOR UPDATE SKIP LOCKED}): başka bir instance'ın
 *       kilitlediği satırlar atlanır, aynı mesajı iki instance göndermez.</li>
 *   <li>Hepsini art arda gönder, SONRA onayları bekle. Faz 1'de her mesajdan sonra bekliyorduk
 *       (acıtan nokta 7); burada onaylar paralel gelir, bir tur ≈ bir gidiş-dönüş süresi.</li>
 *   <li>Onaylanan ve bir kuyruğa ulaşan mesajları {@code published_at} ile işaretle. Başarısız olanlar
 *       deneme sayısı ve hata ile outbox'ta kalır; sonraki turda tekrar denenir.</li>
 * </ol>
 *
 * <p>At-least-once: Mesaj gönderildi, onay geldi ama {@code published_at} yazılamadan uygulama çöktü
 * → mesaj bir sonraki turda TEKRAR gönderilir. Bu yüzden Storefront tekrarları ayıklamalı (P2-M5);
 * aynı mesaj tekrar gönderildiğinde {@code message_id} de aynıdır.
 *
 * <p>Sıra: başarısız bir mesaj arkasındakileri bekletmez. Storefront versiyon kontrolü yaptığı için
 * sıra bozulsa da sonuç doğru olur; tek bir bozuk mesaj tüm akışı kilitlemez.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final JdbcClient jdbc;
    private final TransactionTemplate transaction;
    private final RabbitTemplate rabbit;
    private final OutboxProperties properties;

    OutboxRelay(JdbcClient jdbc, TransactionTemplate transaction, RabbitTemplate rabbit, OutboxProperties properties) {
        this.jdbc = jdbc;
        this.transaction = transaction;
        this.rabbit = rabbit;
        this.properties = properties;
    }

    /** Bir tur çalıştırır. @return bu turda yayınlanan mesaj sayısı */
    public int relayPending() {
        Integer published = transaction.execute(status -> {
            List<OutboxRow> rows = lockPending();
            List<Sent> sent = rows.stream().map(this::send).toList();

            List<Long> succeeded = new ArrayList<>();
            for (Sent message : sent) {
                String error = awaitOutcome(message);
                if (error == null) {
                    succeeded.add(message.row().id());
                } else {
                    markFailed(message.row(), error);
                }
            }
            markPublished(succeeded);
            return succeeded.size();
        });
        return published == null ? 0 : published;
    }

    private List<OutboxRow> lockPending() {
        return jdbc.sql("""
                        select id, message_id::text, message_type, routing_key, payload::text
                        from outbox
                        where published_at is null
                        order by id
                        limit ?
                        for update skip locked""")
                .param(properties.batchSize())
                .query((rs, n) -> new OutboxRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5)))
                .list();
    }

    private Sent send(OutboxRow row) {
        Message message = MessageBuilder.withBody(row.payload().getBytes(UTF_8))
                .setContentType("application/json")
                .setType(row.messageType())
                .setMessageId(row.messageId())
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .build();
        CorrelationData correlation = new CorrelationData(row.messageId());
        try {
            rabbit.send(BackofficeEvents.EXCHANGE, row.routingKey(), message, correlation);
            return new Sent(row, correlation, null);
        } catch (RuntimeException e) {
            return new Sent(row, correlation, e.getMessage()); // ör. broker'a bağlanılamadı
        }
    }

    /** @return hata açıklaması; başarılıysa {@code null} */
    private String awaitOutcome(Sent sent) {
        if (sent.sendError() != null) {
            return "gönderilemedi: " + sent.sendError();
        }
        try {
            CorrelationData.Confirm confirm = sent.correlation().getFuture()
                    .get(properties.confirmTimeout().toMillis(), TimeUnit.MILLISECONDS);
            if (!confirm.ack()) {
                return "broker reddetti (nack): " + confirm.reason();
            }
            // Broker, yönlendirilemeyen mandatory mesajı ack'ten ÖNCE geri gönderir; Spring de onu
            // future tamamlanmadan önce CorrelationData'ya koyar.
            ReturnedMessage returned = sent.correlation().getReturned();
            if (returned != null) {
                return "geri döndü: %s (%d) routingKey='%s'".formatted(
                        returned.getReplyText(), returned.getReplyCode(), returned.getRoutingKey());
            }
            return null;
        } catch (TimeoutException e) {
            return "onay zaman aşımı";
        } catch (ExecutionException e) {
            return "onay alınamadı: " + e.getCause().getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "onay beklerken kesildi";
        }
    }

    private void markPublished(List<Long> ids) {
        if (ids.isEmpty()) {
            return;
        }
        jdbc.sql("update outbox set published_at = now() where id in (:ids)").param("ids", ids).update();
    }

    private void markFailed(OutboxRow row, String error) {
        log.warn("Outbox mesajı yayınlanamadı, sonraki turda tekrar denenecek: id={} type={} — {}",
                row.id(), row.messageType(), error);
        jdbc.sql("update outbox set attempts = attempts + 1, last_error = ? where id = ?")
                .params(error, row.id())
                .update();
    }

    private record OutboxRow(long id, String messageId, String messageType, String routingKey, String payload) {
    }

    private record Sent(OutboxRow row, CorrelationData correlation, String sendError) {
    }
}
