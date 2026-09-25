package com.messaging.ads.campaign.launch;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.campaign.enums.CreativeVariantType;
import org.junit.jupiter.api.Test;

class MetaPlacementCreativeVariantSelectorTests {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void selectsStoryVariantForStoryOnlyPlacements() throws Exception {
    var override =
        objectMapper.readTree(
            "{\"publisherPlatforms\":[\"facebook\",\"instagram\"],"
                + "\"facebookPositions\":[\"story\"],"
                + "\"instagramPositions\":[\"story\"]}");

    assertThat(MetaPlacementCreativeVariantSelector.select(override))
        .isEqualTo(CreativeVariantType.META_STORY);
  }

  @Test
  void selectsReelVariantForReelsOnlyPlacements() throws Exception {
    var override =
        objectMapper.readTree(
            "{\"publisherPlatforms\":[\"facebook\",\"instagram\"],"
                + "\"facebookPositions\":[\"reels\"],"
                + "\"instagramPositions\":[\"reels\"]}");

    assertThat(MetaPlacementCreativeVariantSelector.select(override))
        .isEqualTo(CreativeVariantType.META_REEL);
  }

  @Test
  void selectsFeedVariantForFeedLikePlacements() throws Exception {
    var override =
        objectMapper.readTree(
            "{\"publisherPlatforms\":[\"facebook\",\"instagram\"],"
                + "\"facebookPositions\":[\"feed\",\"marketplace\"],"
                + "\"instagramPositions\":[\"stream\",\"explore\"]}");

    assertThat(MetaPlacementCreativeVariantSelector.select(override))
        .isEqualTo(CreativeVariantType.META_FEED);
  }

  @Test
  void returnsNullForMixedPlacementFamilies() throws Exception {
    var override =
        objectMapper.readTree(
            "{\"publisherPlatforms\":[\"instagram\"],"
                + "\"instagramPositions\":[\"stream\",\"story\",\"reels\"]}");

    assertThat(MetaPlacementCreativeVariantSelector.select(override)).isNull();
  }

  @Test
  void returnsNullForAutomaticOrPlatformWidePlacements() throws Exception {
    assertThat(MetaPlacementCreativeVariantSelector.select(objectMapper.readTree("{}"))).isNull();
    assertThat(
            MetaPlacementCreativeVariantSelector.select(
                objectMapper.readTree("{\"publisherPlatforms\":[\"instagram\"]}")))
        .isNull();
  }
}
