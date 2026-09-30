package com.messaging.channel.meta.runtime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MetaWhatsAppProviderMediaUrlPolicyTests {

  private final MetaWhatsAppProviderMediaUrlPolicy policy =
      new MetaWhatsAppProviderMediaUrlPolicy();

  @Test
  void acceptsOnlyHttpsMetaOwnedHosts() {
    assertThatCode(
            () ->
                policy.requireTrusted(
                    "https://lookaside.fbsbx.com/whatsapp_business/attachments/?mid=1"))
        .doesNotThrowAnyException();
    assertThatCode(
            () ->
                policy.requireTrusted(
                    "https://scontent.xx.fbcdn.net/v/t62.7118-24/example"))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsArbitraryHostSuffixTricksAndNonHttpsUrls() {
    assertThatThrownBy(
            () -> policy.requireTrusted("https://facebook.com.attacker.example/file"))
        .isInstanceOf(MetaWhatsAppRuntimeException.class);
    assertThatThrownBy(
            () -> policy.requireTrusted("https://attacker-facebook.com/file"))
        .isInstanceOf(MetaWhatsAppRuntimeException.class);
    assertThatThrownBy(
            () -> policy.requireTrusted("http://lookaside.fbsbx.com/file"))
        .isInstanceOf(MetaWhatsAppRuntimeException.class);
    assertThatThrownBy(
            () -> policy.requireTrusted("https://user@fbcdn.net/file"))
        .isInstanceOf(MetaWhatsAppRuntimeException.class);
  }
}
