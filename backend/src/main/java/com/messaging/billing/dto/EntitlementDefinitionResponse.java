package com.messaging.billing.dto;

import com.messaging.billing.enums.EntitlementCategory;
import com.messaging.billing.enums.EntitlementValueType;

public record EntitlementDefinitionResponse(
    String key,
    String label,
    String description,
    EntitlementValueType valueType,
    EntitlementCategory category,
    String defaultValue,
    boolean allowUnlimited) {}
