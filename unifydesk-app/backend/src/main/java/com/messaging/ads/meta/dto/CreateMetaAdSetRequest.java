package com.messaging.ads.meta.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public record CreateMetaAdSetRequest(
    @NotBlank @Size(max = 255) String name,
    @NotBlank String optimizationGoal,
    @NotBlank String billingEvent,
    String bidStrategy,
    @Positive Long bidAmountMinor,
    @Positive Long dailyBudgetMinor,
    @Positive Long lifetimeBudgetMinor,
    Instant startAt,
    Instant endAt,
    String destinationType,
    @Valid @NotEmpty List<@Valid TargetingLocation> locations,
    @Min(13) @Max(65) Integer ageMin,
    @Min(13) @Max(65) Integer ageMax,
    List<Integer> genders,
    List<String> publisherPlatforms,
    List<String> facebookPositions,
    List<String> instagramPositions,
    List<String> messengerPositions,
    List<String> audienceNetworkPositions,
    List<String> customAudienceIds,
    List<String> excludedCustomAudienceIds,
    Boolean advantageAudience,
    Boolean advantageAgeSuggestion,
    Boolean advantageGenderSuggestion,
    String pixelId,
    String customEventType,
    String customConversionId,
    String productSetId) {

  public CreateMetaAdSetRequest(
      String name,
      String optimizationGoal,
      String billingEvent,
      String bidStrategy,
      Long bidAmountMinor,
      Long dailyBudgetMinor,
      Long lifetimeBudgetMinor,
      Instant startAt,
      Instant endAt,
      String destinationType,
      List<TargetingLocation> locations,
      Integer ageMin,
      Integer ageMax,
      List<Integer> genders,
      List<String> publisherPlatforms,
      List<String> facebookPositions,
      List<String> instagramPositions,
      List<String> messengerPositions,
      List<String> audienceNetworkPositions,
      List<String> customAudienceIds,
      List<String> excludedCustomAudienceIds,
      Boolean advantageAudience,
      Boolean advantageAgeSuggestion,
      Boolean advantageGenderSuggestion,
      String pixelId,
      String customEventType,
      String customConversionId) {
    this(
        name,
        optimizationGoal,
        billingEvent,
        bidStrategy,
        bidAmountMinor,
        dailyBudgetMinor,
        lifetimeBudgetMinor,
        startAt,
        endAt,
        destinationType,
        locations,
        ageMin,
        ageMax,
        genders,
        publisherPlatforms,
        facebookPositions,
        instagramPositions,
        messengerPositions,
        audienceNetworkPositions,
        customAudienceIds,
        excludedCustomAudienceIds,
        advantageAudience,
        advantageAgeSuggestion,
        advantageGenderSuggestion,
        pixelId,
        customEventType,
        customConversionId,
        null);
  }

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

  public record TargetingLocation(
      String countryCode,
      String type,
      String key,
      @Size(max = 255) String name,
      @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
      @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
      @Positive Double radiusKm,
      List<String> locationTypes) {

    public TargetingLocation(String countryCode, List<String> locationTypes) {
      this(
          countryCode,
          "COUNTRY",
          countryCode,
          countryCode,
          null,
          null,
          null,
          locationTypes);
    }
  }
}
