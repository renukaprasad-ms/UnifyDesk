package com.messaging.managedsms.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import com.messaging.managedsms.runtime.ManagedSmsRuntimeProperties;
import org.junit.jupiter.api.Test;

class ManagedSmsProviderCredentialResolverTests {

  @Test
  void childModeNeverFallsBackToMasterCredential() {
    ManagedSmsRuntimeProperties properties = properties();
    properties.setAllowSharedMaster(true);
    DefaultManagedSmsProviderCredentialResolver resolver =
        new DefaultManagedSmsProviderCredentialResolver(properties);

    assertThat(resolver.resolve("CHILD_PER_COMPANY", "client-a")).isEmpty();
  }

  @Test
  void sharedMasterRequiresExplicitOptInAndExactScope() {
    ManagedSmsRuntimeProperties properties = properties();
    DefaultManagedSmsProviderCredentialResolver resolver =
        new DefaultManagedSmsProviderCredentialResolver(properties);

    assertThat(resolver.resolve("SHARED_MASTER", "master")).isEmpty();

    properties.setAllowSharedMaster(true);
    assertThat(resolver.resolve("SHARED_MASTER", "other-account")).isEmpty();
    assertThat(resolver.resolve("SHARED_MASTER", "master"))
        .get()
        .satisfies(
            credential -> {
              assertThat(credential.providerAccountKey()).isEqualTo("master");
              assertThat(credential.authKey()).isEqualTo("master-secret");
            });
  }

  private ManagedSmsRuntimeProperties properties() {
    ManagedSmsRuntimeProperties properties = new ManagedSmsRuntimeProperties();
    properties.setEnabled(true);
    properties.setPlatformSendingEnabled(true);
    properties.setProvider("MSG91_SMS");
    properties.setMasterAuthKey("master-secret");
    return properties;
  }
}
