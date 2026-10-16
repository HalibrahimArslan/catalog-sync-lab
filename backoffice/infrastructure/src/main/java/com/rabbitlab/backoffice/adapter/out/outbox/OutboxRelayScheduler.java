package com.rabbitlab.backoffice.adapter.out.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Relay'i döndürür. İki tetik var:
 * <ul>
 *   <li>{@link #wakeUp()}: outbox'a satır eklendi bildirimi ({@link OutboxNotificationListener}). Asıl yol.</li>
 *   <li>{@code poll-interval}: yedek. Bildirim kaçarsa (dinleyici bağlantısı koptu, yeniden bağlanırken
 *       satır eklendi) mesaj en geç bu kadar gecikir.</li>
 * </ul>
 * Bir tur dolu geldiyse (batch-size kadar) beklemeden devam eder; birikmiş outbox hızla boşalır.
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

    /** Outbox'ta yeni satır var; beklemeden bir tur at. */
    void wakeUp() {
        runNow();
    }

    @Override
    protected void runOnce() {
        while (relay.relayPending() == properties.batchSize()) {
            // dolu tur: arkada daha var
        }
    }
}
