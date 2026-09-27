package com.messaging.managedsms.delivery;

import com.messaging.managedsms.runtime.ManagedSmsRuntimeProperties;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnMissingBean(ManagedSmsProviderCredentialResolver.class)
public class DefaultManagedSmsProviderCredentialResolver
    implements ManagedSmsProviderCredentialResolver {

  private final ManagedSmsRuntimeProperties properties;

  public DefaultManagedSmsProviderCredentialResolver(ManagedSmsRuntimeProperties properties) {
    this.properties = properties;
  }

  @Override
  public Optional<ProviderCredential> resolve(String providerMode, String providerAccountKey) {
    if (!properties.providerConfigured()
        || providerMode == null
        || providerAccountKey == null
        || providerAccountKey.isBlank()) {
      return Optional.empty();
    }

    if ("SHARED_MASTER".equalsIgnoreCase(providerMode)) {
      if (!properties.isAllowSharedMaster()
          || !"master".equals(providerAccountKey)
          || properties.getMasterAuthKey() == null
          || properties.getMasterAuthKey().isBlank()) {
        return Optional.empty();
      }
      return Optional.of(
          new ProviderCredential(providerAccountKey, properties.getMasterAuthKey().trim()));
    }

    // CHILD_PER_COMPANY deliberately has no fallback. A verified provider-contract-specific
    // resolver must supply the credential for the exact child account.
    return Optional.empty();
  }
}
