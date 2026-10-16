package com.rabbitlab.storefront.adapter.out.persistence;

import com.rabbitlab.storefront.application.ProcessedUpdates;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** {@link ProcessedUpdates}'in PostgreSQL uygulaması (tablo: processed_message). */
@Component
class JdbcProcessedUpdates implements ProcessedUpdates {

    private final JdbcClient jdbc;

    JdbcProcessedUpdates(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Aynı kimlik eşzamanlı gelirse ikinci insert, birincinin transaction'ı bitene kadar bekler, sonra hiçbir şey yapmaz. */
    @Override
    public boolean markProcessed(String updateId) {
        return jdbc.sql("insert into processed_message (message_id) values (?) on conflict do nothing")
                .param(updateId)
                .update() == 1;
    }

    /**
     * Saklama süresi dolmuş kimlikleri siler (veritabanının saatiyle). Arayüzde değil: temizlik bir
     * altyapı bakım işi, use case'in bilmesi gereken bir şey değil.
     *
     * @return silinen kayıt sayısı
     */
    int forgetOlderThan(Duration retention) {
        return jdbc.sql("delete from processed_message where processed_at < now() - (? * interval '1 second')")
                .param(retention.toSeconds())
                .update();
    }
}
