package com.messaging.company.dto;

public record PermissionDefinitionResponse(
    String key, String resource, String action, String description, boolean assignable) {}
