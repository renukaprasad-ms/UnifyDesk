package com.messaging.ads.repository;

import com.messaging.ads.entity.AdProviderCredential;
import com.messaging.ads.enums.AdCredentialType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdProviderCredentialRepository
    extends JpaRepository<AdProviderCredential, Long> {

  Optional<AdProviderCredential> findByConnectionIdAndCredentialType(
      Long connectionId, AdCredentialType credentialType);

  void deleteByConnectionId(Long connectionId);
}
