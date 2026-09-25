package com.messaging.ads.meta.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public record UpdateMetaAdSetRequest(
    @Size(max = 255) String name,
    String optimizationGoal,
    String billingEvent,
    String bidStrategy,
    @Positive Long bidAmountMinor,
    @Positive Long dailyBudgetMinor,
    @Positive Long lifetimeBudgetMinor,
    Instant startAt,
    Instant endAt,
    String destinationType,
    @Valid List<CreateMetaAdSetRequest.TargetingLocation> locations,
    @Min(13) @Max(65) Integer ageMin,
    @Min(13) @Max(65) Integer ageMax,
    List<Integer> genders,
    List<String> publisherPlatforms,
    List<String> facebookPositions,
    List<String> instagramPositions,
    List<String> customAudienceIds,
    List<String> excludedCustomAudienceIds,
    String pixelId,
    String customEventType,
    String customConversionId) {

  @AssertTrue(message = "Choose either a daily budget or a lifetime budget, not both")
  public boolean hasValidBudgetChoice() {
    return dailyBudgetMinor == null || lifetimeBudgetMinor == null;
  }

  @AssertTrue(message = "Age maximum must be greater than or equal to age minimum")
  public boolean hasValidAgeRange() {
    return ageMin == null || ageMax == null || ageMax >= ageMin;
  }

  @AssertTrue(message = "End time must be after start time")
  public boolean hasValidSchedule() {
    return startAt == null || endAt == null || endAt.isAfter(startAt);
  }
}
