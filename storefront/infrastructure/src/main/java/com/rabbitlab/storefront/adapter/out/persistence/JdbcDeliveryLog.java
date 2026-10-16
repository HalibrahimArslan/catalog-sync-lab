package com.rabbitlab.storefront.adapter.out.persistence;

import com.rabbitlab.storefront.application.DeliveryAttempt;
import com.rabbitlab.storefront.application.DeliveryEntry;
import com.rabbitlab.storefront.application.DeliveryLog;
import com.rabbitlab.storefront.application.DeliveryOutcome;
import com.rabbitlab.storefront.application.DeliveryQuery;
import com.rabbitlab.storefront.application.DeliverySummary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.Array;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * {@link DeliveryLog}'un PostgreSQL uygulaması (tablo: message_attempt). Her deneme bir satır; mesajın
 * özeti (son durum, deneme sayısı, son hata) sorgu anında satırlardan çıkarılır.
 *
 * <p>Kimliksiz mesajlar birleştirilemez: her biri kendi satır kimliğiyle ({@code #id}) ayrı bir mesajdır.
 */
@Component
class JdbcDeliveryLog implements DeliveryLog {

    /**
     * Her mesajın son satırı (DISTINCT ON) ve tüm satırları üzerinden hesaplananlar (pencere). Filtreler
     * satırlara {@code %s} yerine konur; durum filtresi son satıra, dışarıda uygulanır.
     */
    private static final String LATEST = """
            select distinct on (k) id, message_id, message_type, sku, queue, schema_version, outcome, at,
                   count(*) filter (where outcome <> 'REPLAYED') over w as attempts,
                   min(at) over w as first_seen,
                   array_agg(error) filter (where error is not null) over w as errors
            from (select *, coalesce(message_id, '#' || id) as k from message_attempt where true %s) a
            window w as (partition by k order by id rows between unbounded preceding and unbounded following)
            order by k, id desc""";

    private final JdbcClient jdbc;

    JdbcDeliveryLog(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void record(DeliveryAttempt a) {
        jdbc.sql("""
                        insert into message_attempt (message_id, queue, message_type, sku, schema_version, attempt,
                                                     outcome, error)
                        values (?, ?, ?, ?, ?, ?, ?, ?)""")
                .params(a.messageId(), a.queue(), a.type(), a.sku(), a.schemaVersion(), a.attempt(),
                        a.outcome().name(), a.error())
                .update();
    }

    @Override
    public List<DeliverySummary> find(DeliveryQuery query) {
        // Filtreler isteğe bağlı: SQL'i yalnızca verilenlerle kur (PostgreSQL "? is null" için tip çıkaramaz).
        StringBuilder rowFilter = new StringBuilder();
        List<Object> params = new ArrayList<>();
        if (query.sku() != null) {
            rowFilter.append(" and sku = ?");
            params.add(query.sku());
        }
        if (query.messageId() != null) {
            rowFilter.append(" and message_id = ?");
            params.add(query.messageId());
        }
        StringBuilder sql = new StringBuilder("select * from (" + LATEST.formatted(rowFilter) + ") latest");
        if (query.state() != null) {
            sql.append(" where outcome = ?");
            params.add(query.state().name());
        }
        sql.append(" order by id desc limit ?");
        params.add(query.limit());
        return jdbc.sql(sql.toString()).params(params).query((rs, n) -> new DeliverySummary(
                rs.getString("message_id"),
                rs.getString("message_type"),
                rs.getString("sku"),
                rs.getString("queue"),
                (Integer) rs.getObject("schema_version"),
                DeliveryOutcome.valueOf(rs.getString("outcome")),
                rs.getInt("attempts"),
                last(rs.getArray("errors")),
                instant(rs.getTimestamp("first_seen")),
                instant(rs.getTimestamp("at")))).list();
    }

    @Override
    public List<DeliveryEntry> history(String messageId) {
        return jdbc.sql("select attempt, outcome, error, queue, at from message_attempt where message_id = ? order by id")
                .param(messageId)
                .query((rs, n) -> new DeliveryEntry(rs.getInt("attempt"), DeliveryOutcome.valueOf(rs.getString("outcome")),
                        rs.getString("error"), rs.getString("queue"), instant(rs.getTimestamp("at"))))
                .list();
    }

    @Override
    public Map<DeliveryOutcome, Long> countByState() {
        Map<DeliveryOutcome, Long> counts = new EnumMap<>(DeliveryOutcome.class);
        jdbc.sql("select outcome, count(*) from (" + LATEST.formatted("") + ") latest group by outcome")
                .query((rs, n) -> counts.put(DeliveryOutcome.valueOf(rs.getString(1)), rs.getLong(2)))
                .list();
        return counts;
    }

    /**
     * Saklama süresi dolmuş denemeleri siler (inbox temizliğiyle aynı tur, aynı süre). Arayüzde değil:
     * temizlik bir altyapı bakım işi.
     *
     * @return silinen satır sayısı
     */
    int forgetOlderThan(Duration retention) {
        return jdbc.sql("delete from message_attempt where at < now() - (? * interval '1 second')")
                .param(retention.toSeconds())
                .update();
    }

    private static String last(Array array) throws SQLException {
        if (array == null) {
            return null;
        }
        String[] errors = (String[]) array.getArray();
        return errors.length == 0 ? null : errors[errors.length - 1];
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
