package com.messaging.ads.google.dto;

public record GoogleUserListCustomerTypeResponse(
    String resourceName,
    String userListResourceName,
    String externalUserListId,
    Long localAudienceId,
    String audienceName,
    String customerTypeCategory) {}
