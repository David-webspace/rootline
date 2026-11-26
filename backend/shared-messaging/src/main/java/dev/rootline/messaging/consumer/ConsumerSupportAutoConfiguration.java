package dev.rootline.messaging.consumer;

import java.util.ArrayList;
import java.util.List;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.ClassUtils;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.core.JacksonException;

/**
 * 所有消費端共用的錯誤處理：暫時性錯誤重試 3 次（間隔 0.5 秒），
 * 毒訊息（格式錯誤或違反業務規則，重試也沒用）直接送到 {@code <topic>.DLT}，不卡住後面的訊息。
 * 服務可用 {@code rootline.messaging.non-retryable} 加入自己的業務例外類別。
 */
@AutoConfiguration(afterName = {
        "org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration",
        "org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration"})
public class ConsumerSupportAutoConfiguration {

    @Bean
    @ConditionalOnBean(JdbcTemplate.class)
    @ConditionalOnMissingBean
    ProcessedEventGuard processedEventGuard(JdbcTemplate jdbc) {
        return new ProcessedEventGuard(jdbc);
    }

    @Bean
    @ConditionalOnBean(KafkaTemplate.class)
    @ConditionalOnMissingBean(CommonErrorHandler.class)
    DefaultErrorHandler rootlineKafkaErrorHandler(KafkaTemplate<String, String> template, Environment environment) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template,
                (record, exception) -> new TopicPartition(record.topic() + ".DLT", -1));
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(500, 3));

        List<Class<? extends Exception>> nonRetryable = new ArrayList<>(List.of(
                PoisonEventException.class, IllegalArgumentException.class, JacksonException.class));
        for (String name : environment.getProperty("rootline.messaging.non-retryable", String[].class, new String[0])) {
            nonRetryable.add(load(name.trim()));
        }
        nonRetryable.forEach(handler::addNotRetryableExceptions);
        return handler;
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Exception> load(String className) {
        try {
            return (Class<? extends Exception>) ClassUtils.forName(className, ConsumerSupportAutoConfiguration.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("rootline.messaging.non-retryable: class not found " + className, e);
        }
    }
}
