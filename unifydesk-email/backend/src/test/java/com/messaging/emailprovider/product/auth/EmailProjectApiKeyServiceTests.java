package com.messaging.emailprovider.product.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

import com.messaging.emailprovider.product.model.EmailApiKey;
import com.messaging.emailprovider.product.model.EmailOrganization;
import com.messaging.emailprovider.product.model.EmailProject;
import com.messaging.emailprovider.product.repo.EmailApiKeyRepository;
import com.messaging.emailprovider.product.repo.EmailOrganizationRepository;
import com.messaging.emailprovider.product.repo.EmailProjectRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EmailProjectApiKeyServiceTests {
  private final EmailApiKeyRepository keys = mock(EmailApiKeyRepository.class);
  private final EmailProjectRepository projects = mock(EmailProjectRepository.class);
  private final EmailOrganizationRepository organizations = mock(EmailOrganizationRepository.class);
  private final EmailProjectApiKeyService auth =
      new EmailProjectApiKeyService(keys, projects, organizations);

  @Test
  void acceptsMatchingLiveKeyOnlyWithinItsProjectAndScopes() {
    String prefix = ApiKeyCrypto.prefix();
    String raw = "ude_live_" + prefix + "_" + ApiKeyCrypto.random(32);
    EmailApiKey key = key(prefix, raw);
    EmailProject project = new EmailProject();
    project.setId(50L);
    project.setOrganizationId(100L);
    project.setStatus("ACTIVE");
    project.setSendingEnabled(true);
    EmailOrganization org = new EmailOrganization();
    org.setId(100L);
    org.setStatus("ACTIVE");
    when(keys.findByLookupPrefix(prefix)).thenReturn(Optional.of(key));
    when(projects.findById(50L)).thenReturn(Optional.of(project));
    when(organizations.findById(100L)).thenReturn(Optional.of(org));

    AuthenticatedProject result = auth.authenticate("Bearer " + raw, ApiKeyScope.SEND);
    assertNotNull(result);
    assertEquals(50L, result.projectId());
    assertNull(auth.authenticate("Bearer " + raw, ApiKeyScope.DOMAINS));
    assertNull(auth.authenticate("Bearer " + raw + "invalid", ApiKeyScope.SEND));
    key.setRevokedAt(Instant.now());
    assertNull(auth.authenticate("Bearer " + raw, ApiKeyScope.SEND));
    key.setRevokedAt(null);
    org.setStatus("SUSPENDED");
    assertNull(auth.authenticate("Bearer " + raw, ApiKeyScope.SEND));
  }

  @Test
  void rejectsExpiredKey() {
    String prefix = ApiKeyCrypto.prefix();
    String raw = "ude_live_" + prefix + "_" + ApiKeyCrypto.random(32);
    EmailApiKey key = key(prefix, raw);
    key.setExpiresAt(Instant.now().minusSeconds(60));
    when(keys.findByLookupPrefix(prefix)).thenReturn(Optional.of(key));
    assertNull(auth.authenticate("Bearer " + raw, ApiKeyScope.SEND));
    verifyNoInteractions(projects);
  }

  private EmailApiKey key(String prefix, String raw) {
    EmailApiKey key = new EmailApiKey();
    key.setId(5L);
    key.setProjectId(50L);
    key.setLookupPrefix(prefix);
    key.setSecretHash(ApiKeyCrypto.hash(raw));
    key.setScopes("READ,SEND");
    return key;
  }
}
