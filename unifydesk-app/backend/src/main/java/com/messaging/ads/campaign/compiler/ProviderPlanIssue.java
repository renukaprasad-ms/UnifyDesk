package com.messaging.ads.campaign.compiler;

public record ProviderPlanIssue(
    PlanIssueSeverity severity, String code, String field, String message) {}
