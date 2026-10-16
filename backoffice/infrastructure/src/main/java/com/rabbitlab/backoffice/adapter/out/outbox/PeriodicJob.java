package com.rabbitlab.backoffice.adapter.out.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Arka planda belirli aralıkla çalışan iş. Uygulama ayağa kalkınca başlar, kapanırken durur
 * ({@code SmartLifecycle}). Bir turdaki hata zamanlayıcıyı öldürmez; sonraki turda tekrar denenir.
 */
abstract class PeriodicJob implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(PeriodicJob.class);
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);

    private final String name;
    private final Duration interval;
    private ScheduledExecutorService executor;

    protected PeriodicJob(String name, Duration interval) {
        this.name = name;
        this.interval = interval;
    }

    /** Bir tur. */
    protected abstract void runOnce();

    @Override
    public synchronized void start() {
        executor = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name(name).factory());
        executor.scheduleWithFixedDelay(this::safeRun, 0, interval.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void safeRun() {
        try {
            runOnce();
        } catch (RuntimeException e) {
            log.warn("{} turu başarısız, sonraki turda tekrar denenecek", name, e);
        }
    }

    @Override
    public synchronized void stop() {
        if (executor != null) {
            executor.shutdown();
            try {
                executor.awaitTermination(SHUTDOWN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            executor = null;
        }
    }

    @Override
    public synchronized boolean isRunning() {
        return executor != null;
    }
}
