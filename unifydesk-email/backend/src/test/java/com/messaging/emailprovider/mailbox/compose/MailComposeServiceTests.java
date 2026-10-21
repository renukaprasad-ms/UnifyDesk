package com.messaging.emailprovider.mailbox.compose;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.messaging.emailprovider.common.exception.BadRequestException;
import com.messaging.emailprovider.common.exception.ServiceUnavailableException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MailComposeServiceTests {
  @Test
  void recipientsAreCanonicalAndDeduplicatedAcrossLists() {
    Set<String> seen = new HashSet<>();
    assertEquals("person@example.com",
        MailComposeService.canonical(List.of("Person@Example.COM"), seen));
    assertThrows(BadRequestException.class,
        () -> MailComposeService.canonical(List.of("person@example.com"), seen));
  }

  @Test
  void rejectingUnsafeRecipientValuesPreventsHeaderInjection() {
    for (String bad : List.of("bad\nBcc:victim@example.com",
        "Display Name <person@example.com>", "person@localhost",
        "a..b@example.com", "a@example.com,second@example.com",
        "person @example.com")) {
      assertThrows(BadRequestException.class,
          () -> MailComposeService.canonical(List.of(bad), new HashSet<>()), bad);
    }
  }

  @Test
  void enforcesRecipientCountAndRefusesMissingInput() {
    assertEquals("", MailComposeService.canonical(null, new HashSet<>()));
    assertThrows(BadRequestException.class,
        () -> MailComposeService.canonical(
            java.util.stream.IntStream.range(0, 21)
                .mapToObj(i -> "person" + i + "@example.com").toList(),
            new HashSet<>()));
  }

  @Test
  void submissionStaysDisabledWithoutMailboxTransport() {
    MailComposeService service = new MailComposeService(null, false);
    assertThrows(ServiceUnavailableException.class,
        () -> service.hold(null, 123L, 456L, "the-idempotency-key"));
  }
}
