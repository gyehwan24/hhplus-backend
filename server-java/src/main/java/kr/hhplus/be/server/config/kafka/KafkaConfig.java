package kr.hhplus.be.server.config.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka 설정
 * 토픽 정의 및 기본 설정
 */
@Configuration
public class KafkaConfig {

    public static final String PAYMENT_COMPLETED_TOPIC = "payment.completed";

    @Bean
    public NewTopic paymentCompletedTopic() {
        return TopicBuilder.name(PAYMENT_COMPLETED_TOPIC)
            .partitions(3)
            .replicas(3)
            .build();
    }
}
