package com.rabbitlab.backoffice.adapter.in.web;

import com.rabbitlab.backoffice.application.OutboxEntry;
import com.rabbitlab.backoffice.application.OutboxQueries;
import com.rabbitlab.backoffice.application.OutboxQuery;
import com.rabbitlab.backoffice.application.OutboxState;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Mesaj takibinin Backoffice tarafı: outbox'taki mesajlar ve durumları. Storefront'un
 * {@code /admin/messages} uç noktasıyla aynı message-id üzerinden birleşir.
 *
 * <p>Yalnızca yöneticiye açık (HTTP Basic, {@link AdminSecurityConfiguration}); kullanıcı adı ve şifre
 * {@code ADMIN_USER} / {@code ADMIN_PASSWORD} ayarından.
 *
 * <pre>
 *   GET /admin/outbox?sku=&amp;messageId=&amp;state=PENDING|FAILING|PUBLISHED&amp;limit=50
 *   GET /admin/outbox/stats      → {"PENDING": n, "FAILING": n, "PUBLISHED": n}
 * </pre>
 */
@RestController
@RequestMapping("/admin/outbox")
class OutboxAdminController {

    private final OutboxQueries queries;

    OutboxAdminController(OutboxQueries queries) {
        this.queries = queries;
    }

    @GetMapping
    ResponseEntity<List<OutboxEntry>> find(@RequestParam(required = false) String sku,
                                           @RequestParam(required = false) String messageId,
                                           @RequestParam(required = false) OutboxState state,
                                           @RequestParam(defaultValue = "50") int limit) {
        if (limit < 1) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(queries.find(new OutboxQuery(sku, messageId, state, limit)));
    }

    /** Her durum yer alır; olmayanlar 0 (grafikte boşluk kalmasın). */
    @GetMapping("/stats")
    Map<OutboxState, Long> stats() {
        Map<OutboxState, Long> counts = new EnumMap<>(OutboxState.class);
        for (OutboxState state : OutboxState.values()) {
            counts.put(state, 0L);
        }
        counts.putAll(queries.countByState());
        return counts;
    }
}
