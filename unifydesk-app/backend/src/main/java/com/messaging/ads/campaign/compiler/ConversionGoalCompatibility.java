package com.messaging.ads.campaign.compiler;

import com.messaging.ads.campaign.enums.CampaignBusinessGoal;
import com.messaging.ads.enums.AdProvider;
import java.util.Locale;
import java.util.Set;

final class ConversionGoalCompatibility {

  private ConversionGoalCompatibility() {}

  static boolean shouldWarn(
      CampaignBusinessGoal businessGoal, AdProvider provider, String providerEventName) {
    if (businessGoal == null || provider == null || providerEventName == null
        || providerEventName.isBlank()) {
      return false;
    }
    Set<String> compatible = compatibleEvents(businessGoal, provider);
    if (compatible.isEmpty()) return false;
    return !compatible.contains(normalize(providerEventName));
  }

  static String recommendedLabel(CampaignBusinessGoal businessGoal, AdProvider provider) {
    Set<String> compatible = compatibleEvents(businessGoal, provider);
    return compatible.isEmpty() ? null : String.join(", ", compatible);
  }

  static String defaultMetaStandardEvent(CampaignBusinessGoal businessGoal) {
    if (businessGoal == null) return null;
    return switch (businessGoal) {
      case PURCHASE -> "PURCHASE";
      case LEAD, QUALIFIED_LEAD -> "LEAD";
      case SIGN_UP -> "COMPLETE_REGISTRATION";
      case PHONE_CALL, WHATSAPP_CONVERSATION -> "CONTACT";
      case APP_INSTALL -> "ACTIVATE_APP";
      case TRAFFIC -> "VIEW_CONTENT";
      case AWARENESS -> null;
    };
  }

  private static Set<String> compatibleEvents(
      CampaignBusinessGoal businessGoal, AdProvider provider) {
    if (provider == AdProvider.GOOGLE_ADS) {
      return switch (businessGoal) {
        case PURCHASE -> Set.of("PURCHASE");
        case LEAD -> Set.of(
            "SUBMIT_LEAD_FORM", "IMPORTED_LEAD", "CONTACT", "REQUEST_QUOTE");
        case QUALIFIED_LEAD -> Set.of("QUALIFIED_LEAD", "CONVERTED_LEAD");
        case SIGN_UP -> Set.of("SIGNUP");
        case PHONE_CALL -> Set.of("PHONE_CALL_LEAD");
        case WHATSAPP_CONVERSATION -> Set.of("CONTACT", "IMPORTED_LEAD");
        case APP_INSTALL -> Set.of("DOWNLOAD", "DEFAULT");
        case TRAFFIC, AWARENESS -> Set.of();
      };
    }

    if (provider == AdProvider.META_ADS) {
      return switch (businessGoal) {
        case PURCHASE -> Set.of("PURCHASE");
        case LEAD, QUALIFIED_LEAD -> Set.of("LEAD");
        case SIGN_UP -> Set.of("COMPLETE_REGISTRATION");
        case PHONE_CALL, WHATSAPP_CONVERSATION -> Set.of("CONTACT", "LEAD");
        case APP_INSTALL -> Set.of("ACTIVATE_APP");
        case TRAFFIC -> Set.of("VIEW_CONTENT");
        case AWARENESS -> Set.of();
      };
    }
    return Set.of();
  }

  private static String normalize(String value) {
    return value.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
  }
}
