package com.rabbitlab.backoffice.adapter.out.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Relay'i arka planda {@code poll-interval} aralıkla döndürür. Bir tur dolu geldiyse (batch-size kadar)
 * beklemeden devam eder; birikmiş outbox hızla boşalır.
 */
@Component
@ConditionalOnProperty(name = "backoffice.outbox.scheduling-enabled", havingValue = "true", matchIfMissing = true)
class OutboxRelayScheduler extends PeriodicJob {

    private final OutboxRelay relay;
    private final OutboxProperties properties;

    OutboxRelayScheduler(OutboxRelay relay, OutboxProperties properties) {
        super("outbox-relay", properties.pollInterval());
        this.relay = relay;
        this.properties = properties;
    }

    @Override
    protected void runOnce() {
        while (relay.relayPending() == properties.batchSize()) {
            // dolu tur: arkada daha var
        }
    }
}
