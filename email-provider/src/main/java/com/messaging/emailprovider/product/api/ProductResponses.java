package com.messaging.emailprovider.product.api;

import java.time.Instant;

public final class ProductResponses {
  private ProductResponses() {}

  public record Organization(String id, String name, String contactEmail, String status) {}
  public record Project(String id, String organizationId, String name,
                        String status, boolean sendingEnabled) {}
  public record ApiKey(String id, String projectId, String name, String prefix,
                       String scopes, Instant expiresAt, Instant revokedAt,
                       Instant createdAt) {}
  public record IssuedKey(ApiKey key, String secret) {}
}
