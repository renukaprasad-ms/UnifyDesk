package com.messaging.emailprovider.product.auth;

public record AuthenticatedProject(Long projectId, Long organizationId, Long apiKeyId,
                                   boolean sendingEnabled) {}
