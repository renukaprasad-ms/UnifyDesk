package com.messaging.emailprovider.dkim.service;

import com.messaging.emailprovider.common.credential.CredentialCipher;
import com.messaging.emailprovider.common.exception.NotFoundException;
import com.messaging.emailprovider.dkim.entity.DkimKey;
import com.messaging.emailprovider.dkim.repository.DkimKeyRepository;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DkimKeyService {

  private final DkimKeyRepository repository;
  private final DkimKeyGenerator generator;
  private final CredentialCipher credentialCipher;
  private final String selector;

  public DkimKeyService(
      DkimKeyRepository repository,
      DkimKeyGenerator generator,
      CredentialCipher credentialCipher,
      @Value("${app.dkim.selector:rly1}") String selector) {
    this.repository = repository;
    this.generator = generator;
    this.credentialCipher = credentialCipher;
    this.selector = selector;
  }

  @Transactional
  public DkimKey createForDomain(Long sendingDomainId) {
    DkimKeyMaterial material = generator.generate();

    DkimKey key = new DkimKey();
    key.setSendingDomainId(sendingDomainId);
    key.setSelector(selector);
    key.setEncryptedPrivateKey(credentialCipher.encrypt(material.privateKey()));
    key.setPublicKey(material.publicKey());
    return repository.save(key);
  }

  @Transactional(readOnly = true)
  public DkimKey getActiveForDomain(Long sendingDomainId) {
    return repository
        .findFirstBySendingDomainIdAndActiveTrueOrderByCreatedAtDesc(sendingDomainId)
        .orElseThrow(() -> new NotFoundException("Active DKIM key not found"));
  }

  @Transactional(readOnly = true)
  public PrivateKey loadPrivateKey(Long sendingDomainId) {
    DkimKey key = getActiveForDomain(sendingDomainId);
    String encoded = credentialCipher.decrypt(key.getEncryptedPrivateKey());

    try {
      byte[] bytes = Base64.getDecoder().decode(encoded);
      return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(bytes));
    } catch (GeneralSecurityException | IllegalArgumentException exception) {
      throw new IllegalStateException("Unable to load DKIM private key", exception);
    }
  }
}
