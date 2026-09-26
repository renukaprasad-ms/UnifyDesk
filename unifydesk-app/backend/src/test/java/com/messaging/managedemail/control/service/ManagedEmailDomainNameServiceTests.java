package com.messaging.managedemail.control.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.messaging.common.exception.BadRequestException;
import org.junit.jupiter.api.Test;

class ManagedEmailDomainNameServiceTests {

  private final ManagedEmailDomainNameService service = new ManagedEmailDomainNameService();

  @Test
  void normalizesUnicodeDomainWithIdna() {
    assertThat(service.normalizeCustomerDomain("BÜCHER.de")).isEqualTo("xn--bcher-kva.de");
  }

  @Test
  void rejectsUrlEmailWildcardAndPublicSuffix() {
    assertThatThrownBy(() -> service.normalizeCustomerDomain("https://example.com"))
        .isInstanceOf(BadRequestException.class);
    assertThatThrownBy(() -> service.normalizeCustomerDomain("offers@example.com"))
        .isInstanceOf(BadRequestException.class);
    assertThatThrownBy(() -> service.normalizeCustomerDomain("*.example.com"))
        .isInstanceOf(BadRequestException.class);
    assertThatThrownBy(() -> service.normalizeCustomerDomain("co.uk"))
        .isInstanceOf(BadRequestException.class);
  }

  @Test
  void generatesOpaqueHostedNameAndProtectsReservedAndProhibitedPrefixes() {
    var first = service.generateHostedName("API", "unifydesk.in");
    var second = service.generateHostedName("API", "unifydesk.in");

    assertThat(first.domain()).matches("company-[a-z2-7]{6}\\.unifydesk\\.in");
    assertThat(second.domain()).isNotEqualTo(first.domain());
    assertThatThrownBy(() -> service.generateHostedName("Scam Shop", "unifydesk.in"))
        .isInstanceOf(BadRequestException.class);
  }
}
