package io.radius.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.radius.common.outbox.OutboxPublisher;
import io.radius.common.outbox.OutboxRepository;
import io.radius.common.outbox.OutboxService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

@AutoConfiguration
@ConditionalOnClass(KafkaTemplate.class)
@ConditionalOnProperty(prefix = "radius.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableScheduling
public class RadiusOutboxAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public OutboxService outboxService(OutboxRepository repo, ObjectMapper mapper) {
        return new OutboxService(repo, mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public OutboxPublisher outboxPublisher(OutboxRepository repo, KafkaTemplate<String, String> kafka) {
        return new OutboxPublisher(repo, kafka);
    }
}
