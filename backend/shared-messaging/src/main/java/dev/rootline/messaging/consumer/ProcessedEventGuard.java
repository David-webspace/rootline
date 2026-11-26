package dev.rootline.messaging.consumer;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 消費端冪等。Kafka 與 outbox 都是「至少一次」，同一事件可能送來多次：
 * 在處理事件的同一個 transaction 裡寫入 processed_events，主鍵擋重複；處理失敗 rollback 時標記一起撤銷。
 */
public class ProcessedEventGuard {

    private final JdbcTemplate jdbc;

    public ProcessedEventGuard(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** @return 第一次看到時為 true；已處理過為 false，呼叫端應直接略過 */
    public boolean firstTime(UUID eventId, String consumer) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("ProcessedEventGuard must run inside the event-handling transaction");
        }
        return jdbc.update("INSERT INTO processed_events (event_id, consumer_group) VALUES (?, ?) ON CONFLICT DO NOTHING",
                eventId, consumer) == 1;
    }
}
