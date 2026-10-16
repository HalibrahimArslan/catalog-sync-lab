package com.rabbitlab.storefront.adapter.out.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Saklama süresi dolmuş inbox kayıtlarını ve teslimat geçmişini (mesaj takibi) {@code cleanup-interval}
 * aralıkla siler. İkisi aynı süreyi kullanır: geçmişte görünen bir mesaj inbox'ta da hatırlanıyor olsun.
 */
@Component
@EnableConfigurationProperties(InboxProperties.class)
@ConditionalOnProperty(name = "storefront.inbox.cleanup-enabled", havingValue = "true", matchIfMissing = true)
class InboxCleanupScheduler extends PeriodicJob {

    private static final Logger log = LoggerFactory.getLogger(InboxCleanupScheduler.class);

    private final JdbcProcessedUpdates processed;
    private final JdbcDeliveryLog deliveries;
    private final InboxProperties properties;

    InboxCleanupScheduler(JdbcProcessedUpdates processed, JdbcDeliveryLog deliveries, InboxProperties properties) {
        super("inbox-cleanup", properties.cleanupInterval());
        this.processed = processed;
        this.deliveries = deliveries;
        this.properties = properties;
    }

    @Override
    protected void runOnce() {
        int deleted = processed.forgetOlderThan(properties.retention());
        int attempts = deliveries.forgetOlderThan(properties.retention());
        if (deleted > 0 || attempts > 0) {
            log.info("Inbox temizliği: {} eski kimlik, {} eski teslimat kaydı silindi", deleted, attempts);
        }
    }
}
