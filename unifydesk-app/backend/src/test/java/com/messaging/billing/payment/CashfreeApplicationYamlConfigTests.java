package com.messaging.billing.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

class CashfreeApplicationYamlConfigTests {

  @Test
  void productionApplicationYamlLoadsWithoutDuplicateKeysAndBindsCanonicalCashfreeDefaults()
      throws IOException {
    ClassPathResource applicationYaml = new ClassPathResource("application.yaml");
    assertThat(applicationYaml.exists()).isTrue();

    YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
    List<PropertySource<?>> yamlSources = loader.load("production-application-yaml", applicationYaml);

    ConfigurableEnvironment environment = new StandardEnvironment();
    environment
        .getPropertySources()
        .remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
    environment
        .getPropertySources()
        .remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
    for (PropertySource<?> source : yamlSources) {
      environment.getPropertySources().addLast(source);
    }

    CashfreeProperties properties =
        Binder.get(environment)
            .bind("app.billing.cashfree", Bindable.of(CashfreeProperties.class))
            .orElseThrow();

    assertThat(properties.getWebhookMaxAgeSeconds()).isEqualTo(300L);
    assertThat(properties.getWebhookFutureSkewSeconds()).isEqualTo(60L);
    assertThat(properties.getAutomaticRenewalClaimSeconds()).isEqualTo(300L);

    assertThat(
            environment.getProperty(
                "app.billing.cashfree.automatic-renewal-interval-ms", Long.class))
        .isEqualTo(60000L);
    assertThat(
            environment.getProperty(
                "app.billing.cashfree.automatic-renewal-initial-delay-ms", Long.class))
        .isEqualTo(60000L);
  }
}
