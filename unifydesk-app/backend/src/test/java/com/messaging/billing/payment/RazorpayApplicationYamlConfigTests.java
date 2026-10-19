package com.messaging.billing.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;

class RazorpayApplicationYamlConfigTests {

  @Test
  void productionApplicationYamlLoadsThroughBootConfigDataAndBindsCanonicalRazorpayDefaults() {
    ConfigurableEnvironment environment = new StandardEnvironment();
    environment
        .getPropertySources()
        .remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
    environment
        .getPropertySources()
        .remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);

    SpringApplication application = new SpringApplication(TestConfiguration.class);
    application.setEnvironment(environment);
    application.setWebApplicationType(WebApplicationType.NONE);
    application.setAddCommandLineProperties(false);
    application.setLogStartupInfo(false);
    application.setDefaultProperties(Map.of("spring.main.banner-mode", "off"));

    try (ConfigurableApplicationContext context = application.run()) {
      RazorpayProperties properties = context.getBean(RazorpayProperties.class);

      assertThat(properties.getWebhookMaxAgeSeconds()).isEqualTo(300L);
      assertThat(properties.getWebhookFutureSkewSeconds()).isEqualTo(60L);
      assertThat(properties.getAutomaticRenewalClaimSeconds()).isEqualTo(300L);
    }
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(RazorpayProperties.class)
  static class TestConfiguration {}
}
