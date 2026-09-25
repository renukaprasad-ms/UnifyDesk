package com.messaging.ads.google.dto;

import com.messaging.ads.campaign.dto.AudienceLocationInput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record GoogleKeywordForecastRequest(
    @NotEmpty @Size(max = 10000) List<@Valid Keyword> keywords,
    @Size(max = 10) List<@Valid AudienceLocationInput> locations,
    @Size(max = 64) String language,
    @NotNull LocalDate startDate,
    @NotNull LocalDate endDate,
    @NotNull BiddingStrategy biddingStrategy,
    @DecimalMin(value = "0.000001", inclusive = true) BigDecimal dailyBudget,
    @DecimalMin(value = "0.000001", inclusive = true) BigDecimal maxCpcBid,
    @DecimalMin(value = "0.000001", inclusive = true) BigDecimal maxCpcBidCeiling) {

  public enum BiddingStrategy {
    MANUAL_CPC,
    MAXIMIZE_CLICKS,
    MAXIMIZE_CONVERSIONS
  }

  public record Keyword(
      @NotNull @Size(min = 1, max = 80) String text,
      @NotNull MatchType matchType) {}

  public enum MatchType {
    BROAD,
    PHRASE,
    EXACT
  }
}
