package com.messaging.emailprovider.product.auth;

import com.messaging.emailprovider.product.model.EmailApiKey;
import com.messaging.emailprovider.product.model.EmailOrganization;
import com.messaging.emailprovider.product.model.EmailProject;
import com.messaging.emailprovider.product.repo.EmailApiKeyRepository;
import com.messaging.emailprovider.product.repo.EmailOrganizationRepository;
import com.messaging.emailprovider.product.repo.EmailProjectRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailProjectApiKeyService {
  private static final String PREFIX = "ude_live_";
  private final EmailApiKeyRepository keys;
  private final EmailProjectRepository projects;
  private final EmailOrganizationRepository organizations;

  public EmailProjectApiKeyService(
      EmailApiKeyRepository keys,
      EmailProjectRepository projects,
      EmailOrganizationRepository organizations) {
    this.keys = keys;
    this.projects = projects;
    this.organizations = organizations;
  }

  @Transactional(readOnly = true)
  public AuthenticatedProject authenticate(String authorization, ApiKeyScope scope) {
    if (authorization == null || !authorization.startsWith("Bearer ")) return null;
    String secret = authorization.substring(7);
    if (!secret.startsWith(PREFIX) || secret.length() > 160) return null;
    int separator = secret.indexOf('_', PREFIX.length());
    if (separator < 0 || separator + 1 >= secret.length()) return null;
    String lookupPrefix = secret.substring(PREFIX.length(), separator);
    if (lookupPrefix.length() != 12 || secret.length() - separator - 1 < 40) return null;

    EmailApiKey key = keys.findByLookupPrefix(lookupPrefix).orElse(null);
    if (key == null
        || key.getRevokedAt() != null
        || (key.getExpiresAt() != null && !key.getExpiresAt().isAfter(Instant.now()))
        || !ApiKeyCrypto.matches(secret, key.getSecretHash())) {
      return null;
    }
    boolean allowed =
        java.util.Arrays.stream(key.getScopes().split(",")).anyMatch(s -> s.equals(scope.name()));
    if (!allowed) return null;

    EmailProject project = projects.findById(key.getProjectId()).orElse(null);
    if (project == null || !"ACTIVE".equals(project.getStatus())) return null;
    EmailOrganization org = organizations.findById(project.getOrganizationId()).orElse(null);
    if (org == null || !"ACTIVE".equals(org.getStatus())) return null;
    return new AuthenticatedProject(
        project.getId(), org.getId(), key.getId(), project.isSendingEnabled());
  }
}
