package com.messaging.ads.service;

import com.messaging.ads.entity.AdProviderConnection;
import com.messaging.ads.entity.AdProviderCredential;
import com.messaging.ads.enums.AdCredentialType;
import com.messaging.ads.repository.AdProviderCredentialRepository;
import com.messaging.common.credential.CredentialCipher;
import com.messaging.common.exception.NotFoundException;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdProviderCredentialService {

  private final AdProviderCredentialRepository repository;
  private final CredentialCipher credentialCipher;

  @Transactional
  public void upsert(
      AdProviderConnection connection,
      AdCredentialType credentialType,
      String plaintextPayload,
      Instant expiresAt) {
    AdProviderCredential credential =
        repository
            .findByConnectionIdAndCredentialType(connection.getId(), credentialType)
            .orElseGet(AdProviderCredential::new);
    credential.setConnection(connection);
    credential.setCredentialType(credentialType);
    credential.setEncryptedPayload(credentialCipher.encrypt(plaintextPayload));
    credential.setExpiresAt(expiresAt);
    repository.save(credential);
  }

  @Transactional(readOnly = true)
  public CredentialValue read(Long connectionId, AdCredentialType credentialType) {
    AdProviderCredential credential =
        repository
            .findByConnectionIdAndCredentialType(connectionId, credentialType)
            .orElseThrow(() -> new NotFoundException("Advertising provider credential not found"));
    return new CredentialValue(
        credentialCipher.decrypt(credential.getEncryptedPayload()), credential.getExpiresAt());
  }

  @Transactional
  public void deleteAll(Long connectionId) {
    repository.deleteByConnectionId(connectionId);
  }

  public record CredentialValue(String plaintextPayload, Instant expiresAt) {}
}
