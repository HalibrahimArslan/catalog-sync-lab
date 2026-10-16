package com.rabbitlab.storefront.adapter.out.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/** Saklama süresi dolmuş inbox kayıtlarını {@code cleanup-interval} aralıkla siler. */
@Component
@EnableConfigurationProperties(InboxProperties.class)
@ConditionalOnProperty(name = "storefront.inbox.cleanup-enabled", havingValue = "true", matchIfMissing = true)
class InboxCleanupScheduler extends PeriodicJob {

    private static final Logger log = LoggerFactory.getLogger(InboxCleanupScheduler.class);

    private final JdbcProcessedUpdates processed;
    private final InboxProperties properties;

    InboxCleanupScheduler(JdbcProcessedUpdates processed, InboxProperties properties) {
        super("inbox-cleanup", properties.cleanupInterval());
        this.processed = processed;
        this.properties = properties;
    }

    @Override
    protected void runOnce() {
        int deleted = processed.forgetOlderThan(properties.retention());
        if (deleted > 0) {
            log.info("Inbox temizliği: {} eski kimlik silindi", deleted);
        }
    }
}
