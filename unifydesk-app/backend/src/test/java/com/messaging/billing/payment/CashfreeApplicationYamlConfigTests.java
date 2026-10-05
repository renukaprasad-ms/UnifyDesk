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

class CashfreeApplicationYamlConfigTests {

  @Test
  void productionApplicationYamlLoadsThroughBootConfigDataAndBindsCanonicalCashfreeDefaults() {
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
      CashfreeProperties properties = context.getBean(CashfreeProperties.class);

      assertThat(properties.getWebhookMaxAgeSeconds()).isEqualTo(300L);
      assertThat(properties.getWebhookFutureSkewSeconds()).isEqualTo(60L);
      assertThat(properties.getAutomaticRenewalClaimSeconds()).isEqualTo(300L);

      assertThat(
              context
                  .getEnvironment()
                  .getProperty(
                      "app.billing.cashfree.automatic-renewal-interval-ms", Long.class))
          .isEqualTo(60000L);
      assertThat(
              context
                  .getEnvironment()
                  .getProperty(
                      "app.billing.cashfree.automatic-renewal-initial-delay-ms", Long.class))
          .isEqualTo(60000L);
    }
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(CashfreeProperties.class)
  static class TestConfiguration {}
}
