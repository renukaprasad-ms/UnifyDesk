package com.messaging.managedsms.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.common.exception.BadRequestException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ManagedSmsTemplateServiceTests {

  private final ManagedSmsTemplateService service =
      new ManagedSmsTemplateService(mock(ManagedSmsRuntimeRepository.class), new ObjectMapper());

  @Test
  void verifiesExactApprovedBodyFingerprintBeforeRendering() {
    String body = "Your order {#var#} has shipped";
    ManagedSmsTemplateService.RenderedTemplate rendered =
        service.render(
            body,
            sha256(body),
            "[{\"key\":\"order_id\",\"type\":\"ALPHANUMERIC\",\"maxLength\":20}]",
            "{}",
            Map.of("order_id", "A123"));

    assertThat(rendered.body()).isEqualTo("Your order A123 has shipped");

    assertThatThrownBy(
            () ->
                service.render(
                    body + "!",
                    sha256(body),
                    "[{\"key\":\"order_id\",\"type\":\"ALPHANUMERIC\",\"maxLength\":20}]",
                    "{}",
                    Map.of("order_id", "A123")))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("MANAGED_SMS_TEMPLATE_NOT_READY");
  }

  @Test
  void exactUrlPolicyAllowsOnlyCanonicalApprovedValue() {
    String body = "Track {#var#}";
    String schema = "[{\"key\":\"tracking_url\",\"type\":\"URL\",\"maxLength\":200}]";
    String policy =
        """
        {"policyVersion":2,"urlRules":[
          {"key":"tracking_url","mode":"EXACT_VALUES",
           "allowedValues":["https://example.com/orders/1"]}
        ],"callbackRules":[]}
        """;

    assertThat(
            service
                .render(
                    body,
                    sha256(body),
                    schema,
                    policy,
                    Map.of("tracking_url", "https://example.com/orders/1"))
                .body())
        .isEqualTo("Track https://example.com/orders/1");

    assertThatThrownBy(
            () ->
                service.render(
                    body,
                    sha256(body),
                    schema,
                    policy,
                    Map.of("tracking_url", "https://evil.example/orders/1")))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("MANAGED_SMS_CTA_NOT_APPROVED");
  }

  @Test
  void hostPolicyEnforcesHttpsHostSubdomainAndPath() {
    String body = "Open {#var#}";
    String schema = "[{\"key\":\"portal_url\",\"type\":\"URL\",\"maxLength\":200}]";
    String exactHost =
        """
        {"policyVersion":2,"urlRules":[
          {"key":"portal_url","mode":"HOST_ALLOWLIST","allowedHosts":["example.com"],
           "allowSubdomains":false,"requireHttps":true,"allowedPathPrefixes":["/orders/"]}
        ],"callbackRules":[]}
        """;

    service.render(
        body,
        sha256(body),
        schema,
        exactHost,
        Map.of("portal_url", "https://example.com/orders/123"));

    for (String rejected :
        new String[] {
          "http://example.com/orders/123",
          "https://sub.example.com/orders/123",
          "https://example.com/admin/123",
          "https://user@example.com/orders/123",
          "https://127.0.0.1/orders/123",
          "https://2130706433/orders/123",
          "https://example.com/%2e%2e/admin",
          "https://example.com/orders/%2e%2e/admin"
        }) {
      assertThatThrownBy(
              () ->
                  service.render(
                      body,
                      sha256(body),
                      schema,
                      exactHost,
                      Map.of("portal_url", rejected)))
          .isInstanceOf(BadRequestException.class);
    }

    String subdomains =
        """
        {"policyVersion":2,"urlRules":[
          {"key":"portal_url","mode":"HOST_ALLOWLIST","allowedHosts":["example.com"],
           "allowSubdomains":true,"requireHttps":true,"allowedPathPrefixes":["/orders/"]}
        ],"callbackRules":[]}
        """;
    service.render(
        body,
        sha256(body),
        schema,
        subdomains,
        Map.of("portal_url", "https://tenant.example.com/orders/123"));
    assertThatThrownBy(
            () ->
                service.render(
                    body,
                    sha256(body),
                    schema,
                    subdomains,
                    Map.of("portal_url", "https://notexample.com/orders/123")))
        .isInstanceOf(BadRequestException.class);
  }

  @Test
  void hostAllowlistAllowsOnlyDefaultPortsForHttpAndHttps() {
    String body = "Open {#var#}";
    String schema = "[{\"key\":\"portal_url\",\"type\":\"URL\",\"maxLength\":200}]";
    String httpsPolicy =
        """
        {"policyVersion":2,"urlRules":[
          {"key":"portal_url","mode":"HOST_ALLOWLIST","allowedHosts":["example.com"],
           "allowSubdomains":false,"requireHttps":true,"allowedPathPrefixes":["/orders/"]}
        ],"callbackRules":[]}
        """;

    for (String allowed :
        new String[] {
          "https://example.com/orders/123",
          "https://example.com:443/orders/123"
        }) {
      service.render(body, sha256(body), schema, httpsPolicy, Map.of("portal_url", allowed));
    }

    assertThatThrownBy(
            () ->
                service.render(
                    body,
                    sha256(body),
                    schema,
                    httpsPolicy,
                    Map.of("portal_url", "https://example.com:8443/orders/123")))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("MANAGED_SMS_CTA_NOT_APPROVED");

    String httpPolicy =
        """
        {"policyVersion":2,"urlRules":[
          {"key":"portal_url","mode":"HOST_ALLOWLIST","allowedHosts":["example.com"],
           "allowSubdomains":false,"requireHttps":false,"allowedPathPrefixes":["/path"]}
        ],"callbackRules":[]}
        """;

    for (String allowed :
        new String[] {
          "http://example.com/path",
          "http://example.com:80/path"
        }) {
      service.render(body, sha256(body), schema, httpPolicy, Map.of("portal_url", allowed));
    }

    assertThatThrownBy(
            () ->
                service.render(
                    body,
                    sha256(body),
                    schema,
                    httpPolicy,
                    Map.of("portal_url", "http://example.com:8080/path")))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("MANAGED_SMS_CTA_NOT_APPROVED");
  }

  @Test
  void hostAllowlistAppliesDefaultPortRuleToAllowedSubdomains() {
    String body = "Open {#var#}";
    String schema = "[{\"key\":\"portal_url\",\"type\":\"URL\",\"maxLength\":200}]";
    String policy =
        """
        {"policyVersion":2,"urlRules":[
          {"key":"portal_url","mode":"HOST_ALLOWLIST","allowedHosts":["example.com"],
           "allowSubdomains":true,"requireHttps":true,"allowedPathPrefixes":["/orders/"]}
        ],"callbackRules":[]}
        """;

    service.render(
        body,
        sha256(body),
        schema,
        policy,
        Map.of("portal_url", "https://tenant.example.com:443/orders/123"));

    assertThatThrownBy(
            () ->
                service.render(
                    body,
                    sha256(body),
                    schema,
                    policy,
                    Map.of("portal_url", "https://tenant.example.com:8443/orders/123")))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("MANAGED_SMS_CTA_NOT_APPROVED");
  }

  @Test
  void exactValuesKeepsExplicitNonDefaultPortSemantics() {
    String body = "Track {#var#}";
    String schema = "[{\"key\":\"tracking_url\",\"type\":\"URL\",\"maxLength\":200}]";
    String policy =
        """
        {"policyVersion":2,"urlRules":[
          {"key":"tracking_url","mode":"EXACT_VALUES",
           "allowedValues":["https://example.com:8443/callback"]}
        ],"callbackRules":[]}
        """;

    service.render(
        body,
        sha256(body),
        schema,
        policy,
        Map.of("tracking_url", "https://example.com:8443/callback"));

    assertThatThrownBy(
            () ->
                service.render(
                    body,
                    sha256(body),
                    schema,
                    policy,
                    Map.of("tracking_url", "https://example.com:9443/callback")))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("MANAGED_SMS_CTA_NOT_APPROVED");
  }

  @Test
  void callbackPolicyRequiresExactNormalizedApprovedValue() {
    String body = "Call {#var#}";
    String schema =
        "[{\"key\":\"support_phone\",\"type\":\"CALLBACK\",\"maxLength\":20}]";
    String policy =
        """
        {"policyVersion":2,"urlRules":[],"callbackRules":[
          {"key":"support_phone","allowedValues":["+91 98765 43210"]}
        ]}
        """;

    service.render(
        body,
        sha256(body),
        schema,
        policy,
        Map.of("support_phone", "+919876543210"));

    assertThatThrownBy(
            () ->
                service.render(
                    body,
                    sha256(body),
                    schema,
                    policy,
                    Map.of("support_phone", "+919999999999")))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("MANAGED_SMS_CTA_NOT_APPROVED");
  }

  @Test
  void ctaVariablesRequirePolicyVersionTwoAndCompleteRules() {
    String body = "Open {#var#}";
    String schema = "[{\"key\":\"link\",\"type\":\"URL\",\"maxLength\":100}]";

    assertThatThrownBy(
            () ->
                service.render(
                    body,
                    sha256(body),
                    schema,
                    "{}",
                    Map.of("link", "https://example.com/a")))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("MANAGED_SMS_CTA_NOT_APPROVED");
  }

  private String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }
}
