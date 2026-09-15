package com.messaging.media.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class MediaHttpConfig {

  @Bean
  public RestClient storageRestClient(MediaStorageProperties properties) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(properties.getHttpConnectTimeout());
    requestFactory.setReadTimeout(properties.getHttpReadTimeout());
    return RestClient.builder().requestFactory(requestFactory).build();
  }
}
