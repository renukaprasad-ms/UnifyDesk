package com.messaging.ads.campaign.compiler;

import static org.assertj.core.api.Assertions.assertThat;

import com.messaging.ads.campaign.enums.CampaignBusinessGoal;
import com.messaging.ads.enums.AdProvider;
import org.junit.jupiter.api.Test;

class ConversionGoalCompatibilityTests {

  @Test
  void recognizesProviderEventsThatMatchBusinessGoal() {
    assertThat(
            ConversionGoalCompatibility.shouldWarn(
                CampaignBusinessGoal.PURCHASE, AdProvider.GOOGLE_ADS, "PURCHASE"))
        .isFalse();
    assertThat(
            ConversionGoalCompatibility.shouldWarn(
                CampaignBusinessGoal.LEAD, AdProvider.META_ADS, "LEAD"))
        .isFalse();
  }

  @Test
  void warnsForObviousMismatchWithoutBlockingUnknownEvents() {
    assertThat(
            ConversionGoalCompatibility.shouldWarn(
                CampaignBusinessGoal.PURCHASE, AdProvider.GOOGLE_ADS, "SIGNUP"))
        .isTrue();
    assertThat(
            ConversionGoalCompatibility.shouldWarn(
                CampaignBusinessGoal.PURCHASE, AdProvider.META_ADS, null))
        .isFalse();
  }

  @Test
  void providesGoalAwareMetaDefault() {
    assertThat(
            ConversionGoalCompatibility.defaultMetaStandardEvent(
                CampaignBusinessGoal.SIGN_UP))
        .isEqualTo("COMPLETE_REGISTRATION");
    assertThat(
            ConversionGoalCompatibility.defaultMetaStandardEvent(
                CampaignBusinessGoal.LEAD))
        .isEqualTo("LEAD");
    assertThat(
            ConversionGoalCompatibility.defaultMetaStandardEvent(
                CampaignBusinessGoal.TRAFFIC))
        .isEqualTo("CONTENT_VIEW");
    assertThat(
            ConversionGoalCompatibility.defaultMetaStandardEvent(
                CampaignBusinessGoal.APP_INSTALL))
        .isNull();
  }
}
