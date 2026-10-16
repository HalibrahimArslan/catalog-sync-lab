package com.rabbitlab.backoffice.application;

import com.rabbitlab.backoffice.domain.event.DomainEvent;

import java.util.List;

/**
 * Domain event'lerinin "giden kutusu". Aggregate ile AYNI transaction'da çağrılmalı.
 *
 * <p>Event'ler burada yayınlanmaz, sadece saklanır. RabbitMQ'ya taşımak relay'in işi (P2-M3).
 */
public interface EventOutbox {

    void append(List<DomainEvent> events);
}
