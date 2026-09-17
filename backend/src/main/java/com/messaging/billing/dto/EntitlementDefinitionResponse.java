package com.messaging.billing.dto;

import com.messaging.billing.enums.EntitlementCategory;
import com.messaging.billing.enums.EntitlementValueType;

public record EntitlementDefinitionResponse(
    String entitlementDefinitionId,
    String key,
    String name,
    String description,
    EntitlementValueType valueType,
    EntitlementCategory category,
    String defaultValue,
    boolean active) {}
