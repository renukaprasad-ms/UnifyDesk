package com.messaging.broadcast.msg91;

import static org.assertj.core.api.Assertions.assertThat;

import com.messaging.emailtemplate.enums.EmailTemplateProviderBindingStatus;
import org.junit.jupiter.api.Test;

class Msg91EmailTemplateStatusMapperTests {

  @Test
  void mapsApprovedStatesToReady() {
    assertThat(Msg91EmailTemplateStatusMapper.map("approved"))
        .isEqualTo(EmailTemplateProviderBindingStatus.READY);
    assertThat(Msg91EmailTemplateStatusMapper.map("ACTIVE"))
        .isEqualTo(EmailTemplateProviderBindingStatus.READY);
  }

  @Test
  void neverTreatsNegativeApprovedOrInactiveAsReady() {
    assertThat(Msg91EmailTemplateStatusMapper.map("not approved"))
        .isEqualTo(EmailTemplateProviderBindingStatus.REJECTED);
    assertThat(Msg91EmailTemplateStatusMapper.map("inactive"))
        .isEqualTo(EmailTemplateProviderBindingStatus.REJECTED);
  }

  @Test
  void mapsPendingStatesToPendingReview() {
    assertThat(Msg91EmailTemplateStatusMapper.map("pending review"))
        .isEqualTo(EmailTemplateProviderBindingStatus.PENDING_REVIEW);
  }

  @Test
  void unknownOrMissingStatusIsNotSendable() {
    assertThat(Msg91EmailTemplateStatusMapper.map(null))
        .isEqualTo(EmailTemplateProviderBindingStatus.SYNCED);
    assertThat(Msg91EmailTemplateStatusMapper.map("some-new-status"))
        .isEqualTo(EmailTemplateProviderBindingStatus.SYNCED);
  }
}
