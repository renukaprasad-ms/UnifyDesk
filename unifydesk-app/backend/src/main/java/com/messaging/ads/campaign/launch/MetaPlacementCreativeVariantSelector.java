package com.messaging.ads.campaign.launch;

import com.fasterxml.jackson.databind.JsonNode;
import com.messaging.ads.campaign.enums.CreativeVariantType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class MetaPlacementCreativeVariantSelector {

  private static final Set<String> FEED_LIKE =
      Set.of(
          "feed",
          "stream",
          "marketplace",
          "video_feeds",
          "search",
          "explore",
          "explore_home",
          "profile_feed");

  private MetaPlacementCreativeVariantSelector() {}

  static CreativeVariantType select(JsonNode override) {
    if (override == null || !override.isObject()) return null;
    List<String> platforms = stringArray(override, "publisherPlatforms");
    if (platforms.isEmpty()) return null;

    List<String> positions = new ArrayList<>();
    positions.addAll(stringArray(override, "facebookPositions"));
    positions.addAll(stringArray(override, "instagramPositions"));
    positions.addAll(stringArray(override, "messengerPositions"));
    positions.addAll(stringArray(override, "audienceNetworkPositions"));
    if (positions.isEmpty()) return null;

    if (positions.stream().allMatch(value -> "story".equals(value))) {
      return CreativeVariantType.META_STORY;
    }
    if (positions.stream().allMatch(value -> "reels".equals(value))) {
      return CreativeVariantType.META_REEL;
    }
    if (positions.stream().allMatch(FEED_LIKE::contains)) {
      return CreativeVariantType.META_FEED;
    }
    return null;
  }

  private static List<String> stringArray(JsonNode node, String key) {
    JsonNode value = node.get(key);
    if (value == null || !value.isArray()) return List.of();
    List<String> result = new ArrayList<>();
    value.forEach(
        item -> {
          if (item.isTextual() && !item.asText().isBlank()) {
            result.add(item.asText().trim().toLowerCase(Locale.ROOT));
          }
        });
    return result;
  }
}
