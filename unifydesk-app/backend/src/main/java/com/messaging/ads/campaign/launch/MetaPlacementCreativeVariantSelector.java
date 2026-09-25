package com.messaging.ads.campaign.launch;

import com.fasterxml.jackson.databind.JsonNode;
import com.messaging.ads.campaign.enums.CreativeVariantType;
import java.util.ArrayList;
import java.util.LinkedHashSet;
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
  private static final Set<String> STORY_LIKE = Set.of("story");
  private static final Set<String> REEL_LIKE = Set.of("reels");

  private MetaPlacementCreativeVariantSelector() {}

  static CreativeVariantType select(JsonNode override) {
    Set<CreativeVariantType> families = selectedFamilies(override);
    return fullyMapped(override) && families.size() == 1 ? families.iterator().next() : null;
  }

  static Set<CreativeVariantType> selectedFamilies(JsonNode override) {
    LinkedHashSet<CreativeVariantType> result = new LinkedHashSet<>();
    for (String position : allPositions(override)) {
      if (FEED_LIKE.contains(position)) result.add(CreativeVariantType.META_FEED);
      else if (STORY_LIKE.contains(position)) result.add(CreativeVariantType.META_STORY);
      else if (REEL_LIKE.contains(position)) result.add(CreativeVariantType.META_REEL);
    }
    return Set.copyOf(result);
  }

  static boolean fullyMapped(JsonNode override) {
    List<String> positions = allPositions(override);
    if (positions.isEmpty()) return false;
    return positions.stream()
        .allMatch(
            value ->
                FEED_LIKE.contains(value)
                    || STORY_LIKE.contains(value)
                    || REEL_LIKE.contains(value));
  }

  static List<String> facebookPositions(JsonNode override, CreativeVariantType type) {
    return positionsForType(stringArray(override, "facebookPositions"), type);
  }

  static List<String> instagramPositions(JsonNode override, CreativeVariantType type) {
    return positionsForType(stringArray(override, "instagramPositions"), type);
  }

  static List<String> messengerPositions(JsonNode override, CreativeVariantType type) {
    return positionsForType(stringArray(override, "messengerPositions"), type);
  }

  static List<String> audienceNetworkPositions(JsonNode override, CreativeVariantType type) {
    return positionsForType(stringArray(override, "audienceNetworkPositions"), type);
  }

  static List<String> publisherPlatforms(JsonNode override, CreativeVariantType type) {
    List<String> result = new ArrayList<>();
    if (!facebookPositions(override, type).isEmpty()) result.add("facebook");
    if (!instagramPositions(override, type).isEmpty()) result.add("instagram");
    if (!messengerPositions(override, type).isEmpty()) result.add("messenger");
    if (!audienceNetworkPositions(override, type).isEmpty()) result.add("audience_network");
    return List.copyOf(result);
  }

  private static List<String> allPositions(JsonNode override) {
    if (override == null || !override.isObject()) return List.of();
    List<String> platforms = stringArray(override, "publisherPlatforms");
    if (platforms.isEmpty()) return List.of();

    List<String> positions = new ArrayList<>();
    positions.addAll(stringArray(override, "facebookPositions"));
    positions.addAll(stringArray(override, "instagramPositions"));
    positions.addAll(stringArray(override, "messengerPositions"));
    positions.addAll(stringArray(override, "audienceNetworkPositions"));
    return List.copyOf(positions);
  }

  private static List<String> positionsForType(
      List<String> positions, CreativeVariantType type) {
    Set<String> allowed =
        switch (type) {
          case META_FEED -> FEED_LIKE;
          case META_STORY -> STORY_LIKE;
          case META_REEL -> REEL_LIKE;
          default -> Set.of();
        };
    return positions.stream().filter(allowed::contains).toList();
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
