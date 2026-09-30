package com.messaging.contacts.processing;

import java.time.Instant;
import java.util.List;

public final class ActivityApiModel {

  private ActivityApiModel() {}

  public record ActivityItem(
      String id,
      String type,
      String title,
      String summary,
      Instant occurredAt,
      String href) {}

  public record ActivityPage(
      List<ActivityItem> items, String nextCursor, boolean hasMore) {}
}
