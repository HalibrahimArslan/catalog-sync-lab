package com.rabbitlab.backoffice.adapter.out.outbox;

import com.rabbitlab.backoffice.application.EventOutbox;
import com.rabbitlab.backoffice.domain.event.DomainEvent;
import com.rabbitlab.contract.BackofficeEvents;
import com.rabbitlab.contract.BackofficeMessage;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.UUID;

/**
 * Event'leri outbox tablosuna, kabloda gidecekleri hâliyle (sözleşme JSON'u + tip + routing key) yazar.
 * Böylece relay hiçbir şeyi çevirmez; satırı olduğu gibi gönderir.
 *
 * <p>Satır, yazıldığı andaki sözleşme sürümünü de taşır (P4-M3): relay başlığı ayardan değil satırdan koyar.
 *
 * <p>Çağıranın transaction'ına katılır: aggregate kaydı geri alınırsa bu satırlar da geri alınır.
 */
@Component
class JdbcEventOutbox implements EventOutbox {

    private final JdbcClient jdbc;
    private final JsonMapper json;
    private final int schemaVersion;

    JdbcEventOutbox(JdbcClient jdbc, JsonMapper json, OutboxProperties properties) {
        this.jdbc = jdbc;
        this.json = json;
        this.schemaVersion = properties.schemaVersion();
    }

    @Override
    public void append(List<DomainEvent> events) {
        for (DomainEvent event : events) {
            BackofficeMessage message = ContractMapper.toMessage(event, schemaVersion);
            jdbc.sql("""
                            insert into outbox (message_id, sku, message_type, routing_key, payload, schema_version)
                            values (?, ?, ?, ?, ?::jsonb, ?)""")
                    .params(UUID.randomUUID(), message.sku(), BackofficeEvents.typeOf(message),
                            BackofficeEvents.routingKeyOf(message), json.writeValueAsString(message), schemaVersion)
                    .update();
        }
    }
}
