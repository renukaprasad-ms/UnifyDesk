package com.messaging.channel.zoho;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.messaging.common.exception.BadRequestException;
import org.junit.jupiter.api.Test;

class ZohoMailDomainsTests {

  @Test
  void resolvesIndiaDataCenter() {
    ZohoMailDomains.Region region =
        ZohoMailDomains.resolve("in", "https://accounts.zoho.in");

    assertThat(region.accountsServer()).isEqualTo("https://accounts.zoho.in");
    assertThat(region.mailApiBaseUrl()).isEqualTo("https://mail.zoho.in");
  }

  @Test
  void rejectsAccountsServerThatDoesNotMatchReturnedLocation() {
    assertThatThrownBy(
            () -> ZohoMailDomains.resolve("in", "https://accounts.zoho.com"))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("does not match");
  }

  @Test
  void validatesOnlyKnownMailApiHosts() {
    assertThat(ZohoMailDomains.validateMailApiBase("https://mail.zoho.in/"))
        .isEqualTo("https://mail.zoho.in");

    assertThatThrownBy(() -> ZohoMailDomains.validateMailApiBase("https://evil.example"))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("not supported");
  }

  @Test
  void rejectsUnknownDataCenter() {
    assertThatThrownBy(() -> ZohoMailDomains.resolve("unknown", null))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("not supported");
  }
}
