package com.rabbitlab.backoffice.adapter.out.outbox;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Yayınlanmış ve saklama süresi ({@code retention}) dolmuş outbox satırlarını siler.
 *
 * <p>Yayınlanmamış satırlara dokunmaz: onlar hâlâ gönderilmeyi bekleyen mesajlar. Süre veritabanının
 * saatiyle ({@code now()}) hesaplanır; uygulama sunucusunun saati kaymış olsa da sonuç değişmez.
 */
@Component
class OutboxCleaner {

    private final JdbcClient jdbc;
    private final OutboxProperties properties;

    OutboxCleaner(JdbcClient jdbc, OutboxProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    /** @return silinen satır sayısı */
    int cleanUp() {
        return jdbc.sql("delete from outbox where published_at < now() - (? * interval '1 second')")
                .param(properties.retention().toSeconds())
                .update();
    }
}
