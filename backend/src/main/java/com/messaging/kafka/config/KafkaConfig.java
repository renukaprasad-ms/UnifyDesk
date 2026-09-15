package com.messaging.kafka.config;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.util.StringUtils;

@EnableKafka
@Configuration
@EnableConfigurationProperties(KafkaProperties.class)
public class KafkaConfig {

  @Bean
  public KafkaAdmin kafkaAdmin(
      @Value("${KAFKA_BOOTSTRAP_SERVERS:${SPRING_KAFKA_BOOTSTRAP_SERVERS:localhost:9001}}")
          String bootstrapServers,
      KafkaProperties kafkaProperties) {
    Map<String, Object> configs = new HashMap<>();
    configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    configs.put(
        CommonClientConfigs.SECURITY_PROTOCOL_CONFIG,
        kafkaProperties.getSecurity().getProtocol());
    if (StringUtils.hasText(kafkaProperties.getSecurity().getSaslMechanism())) {
      configs.put("sasl.mechanism", kafkaProperties.getSecurity().getSaslMechanism());
    }
    if (StringUtils.hasText(kafkaProperties.getSecurity().getSaslJaasConfig())) {
      configs.put("sasl.jaas.config", kafkaProperties.getSecurity().getSaslJaasConfig());
    }
    return new KafkaAdmin(configs);
  }
}
