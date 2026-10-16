package com.rabbitlab.storefront.adapter.in.web;

import com.rabbitlab.storefront.application.ParkedMessage;
import com.rabbitlab.storefront.application.ParkedMessages;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Park kuyruğunun yönetim uç noktaları (P4-M2). Sorunu düzelten insan (ör. eksik ürünü Backoffice'te
 * oluşturan) park edilen mesajı görür ve ana kuyruğa geri gönderir.
 *
 * <p>Kimlik doğrulama yok (Faz 4'te bilinçli kapsam dışı). Gerçek sistemde bu uç noktalar ayrı bir
 * yönetim portunda ya da yetki arkasında olmalı: herkes mesajları okuyup tekrar işletebilir.
 *
 * <pre>
 *   GET  /admin/parking                          park kuyruğu olan kuyruklar
 *   GET  /admin/parking/{queue}?limit=50         park edilenler (yerinde kalır)
 *   POST /admin/parking/{queue}/{messageId}/replay   tek mesajı geri gönder (204 / 404)
 *   POST /admin/parking/{queue}/replay           hepsini geri gönder → {"replayed": n}
 * </pre>
 */
@RestController
@RequestMapping("/admin/parking")
class ParkingController {

    private final ParkedMessages parked;

    ParkingController(ParkedMessages parked) {
        this.parked = parked;
    }

    @GetMapping
    List<String> queues() {
        return parked.queues();
    }

    @GetMapping("/{queue}")
    ResponseEntity<List<ParkedMessage>> list(@PathVariable String queue,
                                             @RequestParam(defaultValue = "50") int limit) {
        if (!known(queue)) {
            return ResponseEntity.notFound().build();
        }
        if (limit < 1) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(parked.list(queue, limit));
    }

    @PostMapping("/{queue}/{messageId}/replay")
    ResponseEntity<Void> replay(@PathVariable String queue, @PathVariable String messageId) {
        if (known(queue) && parked.replay(queue, messageId)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/{queue}/replay")
    ResponseEntity<Map<String, Integer>> replayAll(@PathVariable String queue) {
        if (!known(queue)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("replayed", parked.replayAll(queue)));
    }

    private boolean known(String queue) {
        return parked.queues().contains(queue);
    }
}
