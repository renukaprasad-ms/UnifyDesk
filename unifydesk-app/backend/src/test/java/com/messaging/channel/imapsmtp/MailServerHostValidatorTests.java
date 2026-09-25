package com.messaging.channel.imapsmtp;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.messaging.common.exception.BadRequestException;
import org.junit.jupiter.api.Test;

class MailServerHostValidatorTests {

  private final MailServerHostValidator validator = new MailServerHostValidator();

  @Test
  void rejectsLocalhost() {
    assertThatThrownBy(() -> validator.validate("localhost"))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("Private");
  }

  @Test
  void rejectsLoopbackAddress() {
    assertThatThrownBy(() -> validator.validate("127.0.0.1"))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("Private");
  }

  @Test
  void rejectsPrivateAddress() {
    assertThatThrownBy(() -> validator.validate("10.0.0.1"))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("Private");
  }

  @Test
  void rejectsLinkLocalMetadataRange() {
    assertThatThrownBy(() -> validator.validate("169.254.169.254"))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("Private");
  }
}
