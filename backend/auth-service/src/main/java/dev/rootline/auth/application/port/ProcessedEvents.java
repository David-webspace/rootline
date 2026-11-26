package dev.rootline.auth.application.port;

import java.util.UUID;

/**
 * 消費端冪等：Kafka 保證「至少一次」，同一事件可能送來不只一次。
 * 必須在處理事件的同一個 transaction 內呼叫，處理失敗 rollback 時標記也一起撤銷。
 */
public interface ProcessedEvents {

    /** @return 第一次看到這個事件時為 true；已處理過則為 false，呼叫端應直接略過 */
    boolean markProcessed(UUID eventId, String consumer);
}
