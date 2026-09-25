package com.messaging.channel.repository;

import com.messaging.channel.entity.ProviderCredential;
import com.messaging.channel.enums.CredentialType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderCredentialRepository extends JpaRepository<ProviderCredential, Long> {

  List<ProviderCredential> findByConnectionId(Long connectionId);

  Optional<ProviderCredential> findByConnectionIdAndCredentialType(
      Long connectionId, CredentialType credentialType);

  void deleteByConnectionId(Long connectionId);
}
