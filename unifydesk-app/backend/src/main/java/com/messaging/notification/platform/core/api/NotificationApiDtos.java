package com.messaging.notification.platform.core.api;

import java.time.Instant;
import java.util.List;

public final class NotificationApiDtos {

  private NotificationApiDtos() {}

  public record Scope(String kind, String companyId) {}

  public record Action(String kind, String path) {}

  public record NotificationItem(
      String id,
      String type,
      String category,
      String severity,
      Scope scope,
      String title,
      String body,
      int aggregateCount,
      Action action,
      Instant firstEventAt,
      Instant lastEventAt,
      Instant seenAt,
      Instant readAt,
      boolean unread) {}

  public record NotificationPage(
      List<NotificationItem> items, String nextCursor, boolean hasMore) {}

  public record UnreadCount(long account, long company, long total) {}

  public record SeenRequest(String companyId, List<String> notificationIds) {}

  public record SeenResponse(int updated) {}

  public record ReadStateRequest(String companyId, boolean read) {}

  public record ReadStateResponse(NotificationItem notification, UnreadCount unreadCount) {}

  public record ReadAllRequest(String companyId, Instant before) {}

  public record ReadAllResponse(int updated, Instant cutoffAt, UnreadCount unreadCount) {}

  public record PreferencesRequest(
      String timezone, QuietHoursRequest quietHours, List<TypePreferenceRequest> types) {}

  public record QuietHoursRequest(boolean enabled, String start, String end) {}

  public record TypePreferenceRequest(
      String type, String emailMode, Boolean browserEnabled) {}

  public record PreferencesResponse(
      PreferenceScope scope,
      TimezonePreference timezone,
      QuietHoursResponse quietHours,
      ChannelAvailability channels,
      List<TypePreferenceResponse> types) {}

  public record PreferenceScope(String companyId) {}

  public record TimezonePreference(String value, String source) {}

  public record QuietHoursResponse(boolean enabled, String start, String end) {}

  public record ChannelAvailability(boolean emailAvailable, boolean browserPushConfigured) {}

  public record TypePreferenceResponse(
      String type,
      String label,
      String category,
      boolean mandatoryEmail,
      EmailPreference email,
      BrowserPreference browser) {}

  public record EmailPreference(
      List<String> allowedModes,
      String defaultMode,
      String effectiveMode,
      String overrideMode,
      boolean locked) {}

  public record BrowserPreference(
      boolean defaultEnabled,
      boolean effectiveEnabled,
      Boolean overrideEnabled,
      boolean locked) {}
}
