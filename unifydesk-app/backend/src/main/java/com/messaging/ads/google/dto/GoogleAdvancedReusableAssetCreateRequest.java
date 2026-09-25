package com.messaging.ads.google.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public record GoogleAdvancedReusableAssetCreateRequest(
    @NotNull Type type,
    @Size(max = 128) String name,

    @Size(min = 2, max = 2) String callCountryCode,
    @Size(max = 64) String callPhoneNumber,

    @Size(max = 32) String priceType,
    @Size(max = 32) String priceQualifier,
    @Size(max = 16) String languageCode,
    @Size(min = 3, max = 8) List<@Valid GooglePriceOfferingInput> priceOfferings,

    @Size(max = 64) String promotionTarget,
    @DecimalMin("0.000001") BigDecimal promotionPercentOff,
    @DecimalMin("0.000001") BigDecimal promotionMoneyAmountOff,
    @Size(min = 3, max = 3) String promotionCurrencyCode,
    @Size(max = 32) String promotionDiscountModifier,
    @Size(max = 64) String promotionCode,
    @Size(max = 64) String promotionOccasion,
    @Size(max = 16) String promotionLanguageCode,
    @Size(max = 2048) String finalUrl,

    @Size(max = 64) String leadBusinessName,
    @Size(max = 32) String leadCallToActionType,
    @Size(max = 200) String leadCallToActionDescription,
    @Size(max = 30) String leadHeadline,
    @Size(max = 200) String leadDescription,
    @Size(max = 2048) String leadPrivacyPolicyUrl,
    @Size(max = 20) List<@Size(max = 64) String> leadFields,
    @Size(max = 32) String leadPostSubmitCallToActionType,
    @Size(max = 30) String leadPostSubmitHeadline,
    @Size(max = 200) String leadPostSubmitDescription) {

  public enum Type {
    CALL,
    PRICE,
    PROMOTION,
    LEAD_FORM
  }
}
