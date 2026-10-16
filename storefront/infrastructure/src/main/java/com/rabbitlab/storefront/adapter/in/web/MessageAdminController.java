package com.rabbitlab.storefront.adapter.in.web;

import com.rabbitlab.storefront.application.DeliveryEntry;
import com.rabbitlab.storefront.application.DeliveryLog;
import com.rabbitlab.storefront.application.DeliveryOutcome;
import com.rabbitlab.storefront.application.DeliveryQuery;
import com.rabbitlab.storefront.application.DeliverySummary;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Mesaj takibi: mesajların durumu ve tek bir mesajın deneme geçmişi (Celery'deki Flower'ın görevler
 * sayfası gibi). Backoffice'in {@code /admin/outbox} uç noktasıyla aynı message-id üzerinden birleşir.
 *
 * <p>Kimlik doğrulama yok (park kuyruğu uç noktaları gibi bilinçli kapsam dışı).
 *
 * <pre>
 *   GET /admin/messages?sku=&amp;messageId=&amp;state=PROCESSED|DUPLICATE|RETRY|PARKED|REPLAYED&amp;limit=50
 *   GET /admin/messages/{messageId}   → {"summary": ..., "attempts": [...]}; görülmediyse 404
 *   GET /admin/messages/stats         → durum başına mesaj sayısı
 * </pre>
 */
@RestController
@RequestMapping("/admin/messages")
class MessageAdminController {

    private final DeliveryLog log;

    MessageAdminController(DeliveryLog log) {
        this.log = log;
    }

    record MessageDetail(DeliverySummary summary, List<DeliveryEntry> attempts) {
    }

    @GetMapping
    ResponseEntity<List<DeliverySummary>> find(@RequestParam(required = false) String sku,
                                               @RequestParam(required = false) String messageId,
                                               @RequestParam(required = false) DeliveryOutcome state,
                                               @RequestParam(defaultValue = "50") int limit) {
        if (limit < 1) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(log.find(new DeliveryQuery(sku, messageId, state, limit)));
    }

    @GetMapping("/{messageId}")
    ResponseEntity<MessageDetail> detail(@PathVariable String messageId) {
        return log.find(new DeliveryQuery(null, messageId, null, 1)).stream().findFirst()
                .map(summary -> ResponseEntity.ok(new MessageDetail(summary, log.history(messageId))))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Her durum yer alır; olmayanlar 0. */
    @GetMapping("/stats")
    Map<DeliveryOutcome, Long> stats() {
        Map<DeliveryOutcome, Long> counts = new EnumMap<>(DeliveryOutcome.class);
        for (DeliveryOutcome outcome : DeliveryOutcome.values()) {
            counts.put(outcome, 0L);
        }
        counts.putAll(log.countByState());
        return counts;
    }
}
