package com.messaging.managedemail.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ManagedEmailAddressPolicyTests {

  private final ManagedEmailAddressPolicy policy = new ManagedEmailAddressPolicy();

  @Test
  void acceptsStrictPlainMailboxOnBoundDomain() {
    assertThat(policy.matchesBoundDomain("offers@example.com", "example.com")).isTrue();
  }

  @Test
  void normalizesValidIdnDomainBeforeComparison() {
    assertThat(
            policy.matchesBoundDomain(
                "offers@bücher.example", "xn--bcher-kva.example"))
        .isTrue();
  }

  @Test
  void rejectsMalformedOrUnboundPersistedMailbox() {
    String[] invalid = {
      "ceo@amazon.com",
      "bad..local@example.com",
      ".bad@example.com",
      "bad.@example.com",
      "bad @example.com",
      "bad@example.com extra",
      "Name <offers@example.com>",
      "bad\rexample@example.com",
      "bad\nexample@example.com",
      "bad@@example.com",
      "bad@example..com",
      "bad@-example.com",
      "bad@example-.com",
      "\"offers\"@example.com",
      " offers@example.com",
      "offers@example.com "
    };

    for (String address : invalid) {
      assertThat(policy.matchesBoundDomain(address, "example.com"))
          .as("address=%s", address)
          .isFalse();
    }
  }
}
