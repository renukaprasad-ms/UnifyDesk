package com.messaging.contacts.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.messaging.contacts.core.domain.ContactTypes.IdentityType;
import com.messaging.contacts.core.exception.ContactCoreException;
import org.junit.jupiter.api.Test;

class IdentityNormalizerTests {

  private final IdentityNormalizer normalizer = new IdentityNormalizer();

  @Test
  void emailUsesConservativeCanonicalizationWithoutProviderSpecificRewrite() {
    var normalized =
        normalizer.normalize(
            IdentityType.EMAIL, null, "  First.Last+offer@ExAmPle.COM  ", null);

    assertThat(normalized.displayValue()).isEqualTo("First.Last+offer@ExAmPle.COM");
    assertThat(normalized.normalizedValue()).isEqualTo("first.last+offer@example.com");
  }

  @Test
  void emailIdnaNormalizesDomain() {
    var normalized =
        normalizer.normalize(IdentityType.EMAIL, null, "User@bücher.de", null);

    assertThat(normalized.normalizedValue()).isEqualTo("user@xn--bcher-kva.de");
  }

  @Test
  void nationalPhoneRequiresCountryContextAndCanonicalizesToE164() {
    assertThatThrownBy(
            () -> normalizer.normalize(IdentityType.PHONE, null, "9876543210", null))
        .isInstanceOf(ContactCoreException.class)
        .hasMessage(ContactCoreException.INVALID_IDENTITY);

    var normalized =
        normalizer.normalize(IdentityType.PHONE, null, "9876543210", "IN");

    assertThat(normalized.normalizedValue()).isEqualTo("+919876543210");
  }

  @Test
  void externalIdentityIsOpaqueWithinRequiredNamespace() {
    var normalized =
        normalizer.normalize(IdentityType.EXTERNAL, "CRM", " Customer-ABC ", null);

    assertThat(normalized.namespace()).isEqualTo("CRM");
    assertThat(normalized.normalizedValue()).isEqualTo("Customer-ABC");
  }
}
