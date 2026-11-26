package dev.rootline.messaging.outbox;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param enabled      是否啟動 relay（測試或只讀環境可關閉）
 * @param pollInterval 兩次輪詢之間的間隔；有積壓時會連續處理不等待
 * @param batchSize    每次最多取幾筆
 * @param maxAttempts  超過此送出失敗次數就標記 FAILED，不再重試（需人工處理）
 * @param sendTimeout  等 Kafka ack 的上限
 * @param retention    已送出的事件保留多久後清除
 * @param topics       啟動時確保存在的 topic（Kafka 關閉了自動建立 topic）
 * @param partitions   新建 topic 的 partition 數
 */
@ConfigurationProperties("rootline.outbox.relay")
public record OutboxRelayProperties(Boolean enabled, Duration pollInterval, Integer batchSize, Integer maxAttempts,
                                    Duration sendTimeout, Duration retention, List<String> topics,
                                    Integer partitions) {

    public OutboxRelayProperties {
        enabled = enabled == null || enabled;
        pollInterval = pollInterval == null ? Duration.ofMillis(500) : pollInterval;
        batchSize = batchSize == null ? 100 : batchSize;
        maxAttempts = maxAttempts == null ? 10 : maxAttempts;
        sendTimeout = sendTimeout == null ? Duration.ofSeconds(5) : sendTimeout;
        retention = retention == null ? Duration.ofDays(7) : retention;
        topics = topics == null ? List.of() : List.copyOf(topics);
        partitions = partitions == null ? 3 : partitions;
    }
}
