package com.messaging.managedsms.delivery;

import java.util.Optional;

public interface ManagedSmsProviderCredentialResolver {

  Optional<ProviderCredential> resolve(String providerMode, String providerAccountKey);

  record ProviderCredential(String providerAccountKey, String authKey) {}
}
