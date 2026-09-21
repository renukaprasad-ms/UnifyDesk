package com.messaging.inbox.dto;

import com.messaging.channel.enums.ProviderType;
import java.util.List;

public record InboxEmailChangedEvent(
    String connectionId,
    ProviderType provider,
    List<String> conversationIds,
    List<String> messageIds,
    List<String> deletedMessageIds,
    boolean fullRefreshRequired) {

  private static final int MAX_DELTA_IDS = 100;

  public InboxEmailChangedEvent {
    conversationIds = immutable(conversationIds);
    messageIds = immutable(messageIds);
    deletedMessageIds = immutable(deletedMessageIds);
  }

  public static InboxEmailChangedEvent boundedDelta(
      Long connectionId,
      ProviderType provider,
      java.util.Collection<Long> conversationIds,
      java.util.Collection<Long> messageIds,
      java.util.Collection<Long> deletedMessageIds) {
    int totalIds =
        size(conversationIds) + size(messageIds) + size(deletedMessageIds);
    if (totalIds > MAX_DELTA_IDS) {
      return fullRefresh(connectionId, provider);
    }
    return delta(connectionId, provider, conversationIds, messageIds, deletedMessageIds);
  }

  public static InboxEmailChangedEvent delta(
      Long connectionId,
      ProviderType provider,
      java.util.Collection<Long> conversationIds,
      java.util.Collection<Long> messageIds,
      java.util.Collection<Long> deletedMessageIds) {
    return new InboxEmailChangedEvent(
        connectionId.toString(),
        provider,
        strings(conversationIds),
        strings(messageIds),
        strings(deletedMessageIds),
        false);
  }

  public static InboxEmailChangedEvent fullRefresh(Long connectionId, ProviderType provider) {
    return new InboxEmailChangedEvent(
        connectionId.toString(), provider, List.of(), List.of(), List.of(), true);
  }

  private static int size(java.util.Collection<Long> values) {
    return values == null ? 0 : values.size();
  }

  private static List<String> strings(java.util.Collection<Long> values) {
    if (values == null || values.isEmpty()) return List.of();
    return values.stream().filter(java.util.Objects::nonNull).map(String::valueOf).distinct().toList();
  }

  private static List<String> immutable(List<String> values) {
    return values == null ? List.of() : List.copyOf(values);
  }
}
