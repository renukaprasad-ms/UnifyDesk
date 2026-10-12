package com.messaging.emailprovider.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.messaging.emailprovider.common.exception.BadRequestException;
import org.junit.jupiter.api.Test;

class DomainNameNormalizerTests {

  private final DomainNameNormalizer normalizer = new DomainNameNormalizer();

  @Test
  void normalizesDomain() {
    assertEquals("example.com", normalizer.normalize("  Example.COM. "));
  }

  @Test
  void convertsInternationalDomainToAscii() {
    assertEquals("xn--bcher-kva.de", normalizer.normalize("bücher.de"));
  }

  @Test
  void rejectsUrlInsteadOfDomain() {
    assertThrows(BadRequestException.class, () -> normalizer.normalize("https://example.com"));
  }

  @Test
  void rejectsEmailInsteadOfDomain() {
    assertThrows(BadRequestException.class, () -> normalizer.normalize("user@example.com"));
  }

  @Test
  void rejectsSingleLabelHost() {
    assertThrows(BadRequestException.class, () -> normalizer.normalize("localhost"));
  }
}
