package com.messaging.realtime.config;

import com.messaging.realtime.service.RealtimeRedisSubscriber;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class RealtimeRedisConfig {

  @Bean
  public ChannelTopic realtimeChannel(
      @Value("${app.realtime.redis-channel:messaging:realtime}") String channel) {
    return new ChannelTopic(channel);
  }

  @Bean
  public RedisMessageListenerContainer realtimeRedisMessageListenerContainer(
      RedisConnectionFactory connectionFactory,
      RealtimeRedisSubscriber subscriber,
      ChannelTopic realtimeChannel) {
    RedisMessageListenerContainer container = new RedisMessageListenerContainer();
    container.setConnectionFactory(connectionFactory);
    container.addMessageListener(subscriber, realtimeChannel);
    return container;
  }
}
