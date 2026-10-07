package com.messaging.channel.zoho;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.messaging.common.exception.BadRequestException;
import org.junit.jupiter.api.Test;

class ZohoMailDataCenterTests {

  @Test
  void acceptsKnownRegionalAccountsAndMailHosts() {
    assertThat(ZohoMailDataCenter.trustedAccountsServer("https://accounts.zoho.com"))
        .isEqualTo("https://accounts.zoho.com");
    assertThat(ZohoMailDataCenter.mailBaseUrl("https://accounts.zoho.in"))
        .isEqualTo("https://mail.zoho.in");
    assertThat(ZohoMailDataCenter.mailBaseUrl("https://accounts.zohocloud.ca"))
        .isEqualTo("https://mail.zohocloud.ca");
    assertThat(ZohoMailDataCenter.trustedMailBaseUrl("https://mail.zoho.eu"))
        .isEqualTo("https://mail.zoho.eu");
  }

  @Test
  void rejectsAttackerControlledOrMalformedRegionalHosts() {
    assertThatThrownBy(
            () -> ZohoMailDataCenter.trustedAccountsServer("https://accounts.zoho.com.attacker.test"))
        .isInstanceOf(BadRequestException.class);
    assertThatThrownBy(
            () -> ZohoMailDataCenter.trustedAccountsServer("http://accounts.zoho.com"))
        .isInstanceOf(BadRequestException.class);
    assertThatThrownBy(
            () -> ZohoMailDataCenter.trustedAccountsServer("https://accounts.zoho.com/path"))
        .isInstanceOf(BadRequestException.class);
    assertThatThrownBy(
            () -> ZohoMailDataCenter.trustedMailBaseUrl("https://mail.zoho.com@attacker.test"))
        .isInstanceOf(BadRequestException.class);
  }
}
