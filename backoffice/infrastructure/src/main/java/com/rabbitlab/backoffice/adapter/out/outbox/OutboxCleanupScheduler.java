package com.rabbitlab.backoffice.adapter.out.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Outbox temizliğini {@code cleanup-interval} aralıkla çalıştırır. */
@Component
@ConditionalOnProperty(name = "backoffice.outbox.cleanup-enabled", havingValue = "true", matchIfMissing = true)
class OutboxCleanupScheduler extends PeriodicJob {

    private static final Logger log = LoggerFactory.getLogger(OutboxCleanupScheduler.class);

    private final OutboxCleaner cleaner;

    OutboxCleanupScheduler(OutboxCleaner cleaner, OutboxProperties properties) {
        super("outbox-cleanup", properties.cleanupInterval());
        this.cleaner = cleaner;
    }

    @Override
    protected void runOnce() {
        int deleted = cleaner.cleanUp();
        if (deleted > 0) {
            log.info("Outbox temizliği: {} yayınlanmış satır silindi", deleted);
        }
    }
}
