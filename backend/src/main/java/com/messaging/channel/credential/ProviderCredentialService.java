package com.messaging.channel.credential;

import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.entity.ProviderCredential;
import com.messaging.channel.enums.CredentialType;
import com.messaging.channel.repository.ProviderCredentialRepository;
import com.messaging.common.credential.CredentialCipher;
import com.messaging.common.exception.NotFoundException;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProviderCredentialService {

  private final ProviderCredentialRepository credentialRepository;
  private final CredentialCipher credentialCipher;

  @Transactional
  public void upsert(
      ChannelConnection connection,
      CredentialType credentialType,
      String plaintextPayload,
      Instant expiresAt) {
    ProviderCredential credential =
        credentialRepository
            .findByConnectionIdAndCredentialType(connection.getId(), credentialType)
            .orElseGet(ProviderCredential::new);
    credential.setConnection(connection);
    credential.setCredentialType(credentialType);
    credential.setEncryptedPayload(credentialCipher.encrypt(plaintextPayload));
    credential.setExpiresAt(expiresAt);
    credentialRepository.save(credential);
  }

  @Transactional(readOnly = true)
  public String read(Long connectionId, CredentialType credentialType) {
    return readWithMetadata(connectionId, credentialType).plaintextPayload();
  }

  @Transactional(readOnly = true)
  public CredentialValue readWithMetadata(Long connectionId, CredentialType credentialType) {
    ProviderCredential credential =
        credentialRepository
            .findByConnectionIdAndCredentialType(connectionId, credentialType)
            .orElseThrow(() -> new NotFoundException("Provider credential not found"));

    return new CredentialValue(
        credentialCipher.decrypt(credential.getEncryptedPayload()), credential.getExpiresAt());
  }

  @Transactional
  public void deleteAll(Long connectionId) {
    credentialRepository.deleteByConnectionId(connectionId);
  }

  public record CredentialValue(String plaintextPayload, Instant expiresAt) {}
}
