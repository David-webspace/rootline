package dev.rootline.messaging.outbox;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

/**
 * 背景輪詢執行緒，跟著 Spring context 啟動與停止。
 * 一輪送滿整批代表可能還有積壓，立刻再跑一輪；否則等 pollInterval。
 */
public class OutboxRelayRunner implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayRunner.class);
    private static final Duration PURGE_INTERVAL = Duration.ofHours(1);

    private final OutboxRelay relay;
    private final OutboxRelayProperties properties;
    private ScheduledExecutorService executor;
    private volatile boolean running;
    private long lastPurge;

    public OutboxRelayRunner(OutboxRelay relay, OutboxRelayProperties properties) {
        this.relay = relay;
        this.properties = properties;
    }

    @Override
    public void start() {
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "outbox-relay");
            thread.setDaemon(true);
            return thread;
        });
        running = true;
        executor.execute(this::loop);
    }

    private void loop() {
        long delay = properties.pollInterval().toMillis();
        try {
            int sent = relay.relayBatch();
            if (sent >= properties.batchSize()) {
                delay = 0;
            }
            if (System.currentTimeMillis() - lastPurge > PURGE_INTERVAL.toMillis()) {
                lastPurge = System.currentTimeMillis();
                relay.purgePublished();
            }
        } catch (RuntimeException e) {
            log.warn("Outbox relay cycle failed: {}", e.toString());
        }
        if (running) {
            executor.schedule(this::loop, delay, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void stop() {
        running = false;
        if (executor != null) {
            executor.shutdown();
            try {
                executor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
