package com.rabbitlab.backoffice.adapter.out.outbox;

import com.rabbitlab.backoffice.application.OutboxEntry;
import com.rabbitlab.backoffice.application.OutboxQueries;
import com.rabbitlab.backoffice.application.OutboxQuery;
import com.rabbitlab.backoffice.application.OutboxState;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * {@link OutboxQueries}'in PostgreSQL uygulaması. Durum bir sütun değil, satırdan türetilir: relay
 * {@code published_at}, {@code attempts} ve {@code last_error}'u zaten yazıyor; ayrıca bir durum sütunu
 * tutmak, ikisinin birbirini tutmaması ihtimalini getirirdi.
 */
@Component
class JdbcOutboxQueries implements OutboxQueries {

    private static final String STATE = """
            case when published_at is not null then 'PUBLISHED' when attempts > 0 then 'FAILING' else 'PENDING' end""";

    private static final RowMapper<OutboxEntry> ENTRY = (rs, n) -> new OutboxEntry(
            rs.getString("message_id"),
            rs.getString("sku"),
            rs.getString("message_type"),
            rs.getString("routing_key"),
            rs.getInt("schema_version"),
            rs.getString("payload"),
            instant(rs.getTimestamp("created_at")),
            instant(rs.getTimestamp("published_at")),
            rs.getInt("attempts"),
            rs.getString("last_error"),
            OutboxState.valueOf(rs.getString("state")));

    private final JdbcClient jdbc;

    JdbcOutboxQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<OutboxEntry> find(OutboxQuery query) {
        // Filtreler isteğe bağlı: SQL'i yalnızca verilenlerle kur (PostgreSQL "? is null" için tip çıkaramaz).
        StringBuilder sql = new StringBuilder("""
                select message_id::text, sku, message_type, routing_key, schema_version, payload::text,
                       created_at, published_at, attempts, last_error, %s as state
                from outbox where true""".formatted(STATE));
        List<Object> params = new ArrayList<>();
        if (query.sku() != null) {
            sql.append(" and sku = ?");
            params.add(query.sku());
        }
        if (query.messageId() != null) {
            sql.append(" and message_id::text = ?");
            params.add(query.messageId());
        }
        if (query.state() != null) {
            sql.append(" and ").append(STATE).append(" = ?");
            params.add(query.state().name());
        }
        sql.append(" order by id desc limit ?");
        params.add(query.limit());
        return jdbc.sql(sql.toString()).params(params).query(ENTRY).list();
    }

    @Override
    public Map<OutboxState, Long> countByState() {
        Map<OutboxState, Long> counts = new EnumMap<>(OutboxState.class);
        jdbc.sql("select " + STATE + " as state, count(*) from outbox group by 1")
                .query((rs, n) -> counts.put(OutboxState.valueOf(rs.getString(1)), rs.getLong(2)))
                .list();
        return counts;
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
