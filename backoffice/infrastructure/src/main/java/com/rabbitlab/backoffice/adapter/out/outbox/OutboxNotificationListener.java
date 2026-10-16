package com.rabbitlab.backoffice.adapter.out.outbox;

import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * PostgreSQL'in {@code outbox} kanalını dinler; bildirim gelince relay'i uyandırır.
 *
 * <p>Kendine ait bir veritabanı bağlantısı tutar (havuzdan bir bağlantı sürekli meşgul). Bağlantı
 * koparsa yeniden bağlanır; bu sırada kaçan bildirimleri yedek polling yakalar. Bildirimin içeriği
 * yok: "outbox'ta yeni bir şey var" demesi yeterli, neyin yayınlanacağına relay kendisi bakar.
 */
@Component
@ConditionalOnProperty(name = {"backoffice.outbox.scheduling-enabled", "backoffice.outbox.notify-enabled"},
        havingValue = "true", matchIfMissing = true)
class OutboxNotificationListener implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(OutboxNotificationListener.class);
    private static final int WAIT_MS = 1_000;

    private final DataSource dataSource;
    private final OutboxRelayScheduler relay;
    private volatile boolean running;
    private Thread thread;

    OutboxNotificationListener(DataSource dataSource, OutboxRelayScheduler relay) {
        this.dataSource = dataSource;
        this.relay = relay;
    }

    @Override
    public synchronized void start() {
        running = true;
        thread = Thread.ofPlatform().name("outbox-listen").daemon().start(this::listen);
    }

    private void listen() {
        while (running) {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(true);
                try (Statement statement = connection.createStatement()) {
                    statement.execute("LISTEN outbox");
                }
                PGConnection pg = connection.unwrap(PGConnection.class);
                relay.wakeUp(); // dinlemeye başlamadan önce eklenmiş satırları kaçırmamak için
                while (running) {
                    PGNotification[] notifications = pg.getNotifications(WAIT_MS);
                    if (notifications != null && notifications.length > 0) {
                        relay.wakeUp();
                    }
                }
            } catch (SQLException e) {
                if (running) {
                    log.warn("Outbox bildirim bağlantısı koptu, yeniden bağlanılıyor (bu sırada yedek polling çalışır)", e);
                    pause();
                }
            }
        }
    }

    private static void pause() {
        try {
            Thread.sleep(WAIT_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public synchronized void stop() {
        running = false;
        if (thread != null) {
            try {
                thread.join(WAIT_MS * 3L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            thread = null;
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
