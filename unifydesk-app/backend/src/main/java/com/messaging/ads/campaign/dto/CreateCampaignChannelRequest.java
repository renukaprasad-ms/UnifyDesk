package com.messaging.ads.campaign.dto;

import com.messaging.ads.campaign.enums.CampaignChannelType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record CreateCampaignChannelRequest(
    @NotNull CampaignChannelType channelType,
    Boolean enabled,
    @PositiveOrZero Long budgetAllocationMinor,
    @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal allocationPercent,
    String providerOverrideJson) {}
