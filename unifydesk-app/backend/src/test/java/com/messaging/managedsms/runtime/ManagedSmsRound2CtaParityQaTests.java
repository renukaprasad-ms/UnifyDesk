package com.messaging.managedsms.runtime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.common.exception.BadRequestException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ManagedSmsRound2CtaParityQaTests {

  private final ManagedSmsTemplateService service =
      new ManagedSmsTemplateService(mock(ManagedSmsRuntimeRepository.class), new ObjectMapper());

  @Test
  void hostAllowlistRejectsNonDefaultPortConsistentlyWithControlPlanePreview() {
    String body = "Open {#var#}";
    String schema = "[{\"key\":\"portal_url\",\"type\":\"URL\",\"maxLength\":200}]";
    String policy =
        """
        {"policyVersion":2,"urlRules":[
          {"key":"portal_url","mode":"HOST_ALLOWLIST","allowedHosts":["example.com"],
           "allowSubdomains":false,"requireHttps":true,"allowedPathPrefixes":["/orders/"]}
        ],"callbackRules":[]}
        """;

    assertThatThrownBy(
            () ->
                service.render(
                    body,
                    sha256(body),
                    schema,
                    policy,
                    Map.of("portal_url", "https://example.com:8443/orders/123")))
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
