package dev.rootline.messaging.outbox;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/** 引入此模組的服務自動啟用 relay；設定 {@code rootline.outbox.relay.enabled=false} 可關閉。 */
@AutoConfiguration(afterName = {
        "org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration",
        "org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration",
        "org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration",
        "org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration",
        "org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration"})
@ConditionalOnProperty(prefix = "rootline.outbox.relay", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnBean({KafkaTemplate.class, JdbcTemplate.class})
@EnableConfigurationProperties(OutboxRelayProperties.class)
public class OutboxRelayAutoConfiguration {

    @Bean
    OutboxRelay outboxRelay(JdbcTemplate jdbc, TransactionTemplate tx, KafkaTemplate<String, String> kafka,
                            JsonMapper json, OutboxRelayProperties properties) {
        return new OutboxRelay(jdbc, tx, kafka, json, properties);
    }

    @Bean
    OutboxRelayRunner outboxRelayRunner(OutboxRelay relay, OutboxRelayProperties properties) {
        return new OutboxRelayRunner(relay, properties);
    }

    @Bean
    KafkaAdmin.NewTopics outboxTopics(OutboxRelayProperties properties) {
        return new KafkaAdmin.NewTopics(properties.topics().stream()
                .map(name -> TopicBuilder.name(name).partitions(properties.partitions()).replicas(1).build())
                .toArray(NewTopic[]::new));
    }
}
