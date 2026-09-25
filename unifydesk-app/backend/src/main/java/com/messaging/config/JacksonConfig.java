package com.messaging.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

  @Bean
  public ObjectMapper objectMapper() {
    SimpleModule longAsStringModule = new SimpleModule();
    longAsStringModule.addSerializer(Long.class, ToStringSerializer.instance);
    longAsStringModule.addSerializer(Long.TYPE, ToStringSerializer.instance);

    return new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .registerModule(longAsStringModule)
        .findAndRegisterModules()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  }
}
