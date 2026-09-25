package com.messaging.ads.meta;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public enum MetaStandardConversionEvent {
  ACHIEVEMENT_UNLOCKED,
  ADD_PAYMENT_INFO,
  ADD_TO_CART,
  ADD_TO_WISHLIST,
  AD_IMPRESSION,
  COMPLETE_REGISTRATION,
  CONTACT,
  CONTENT_VIEW,
  CUSTOMIZE_PRODUCT,
  D2_RETENTION,
  D7_RETENTION,
  DONATE,
  FIND_LOCATION,
  INITIATED_CHECKOUT,
  LEAD,
  LEVEL_ACHIEVED,
  LISTING_INTERACTION,
  MESSAGING_CONVERSATION_STARTED_7D,
  OTHER,
  PURCHASE,
  RATE,
  SCHEDULE,
  SEARCH,
  SERVICE_BOOKING_REQUEST,
  SPENT_CREDITS,
  START_TRIAL,
  SUBMIT_APPLICATION,
  SUBSCRIBE,
  TUTORIAL_COMPLETION;

  private static final Set<String> VALUES =
      Arrays.stream(values()).map(Enum::name).collect(Collectors.toUnmodifiableSet());

  public static String normalize(String value) {
    if (value == null || value.isBlank()) return null;
    return value.trim().toUpperCase(Locale.ROOT);
  }

  public static boolean supports(String value) {
    String normalized = normalize(value);
    return normalized != null && VALUES.contains(normalized);
  }
}
