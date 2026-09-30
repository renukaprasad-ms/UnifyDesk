package com.messaging.contacts.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.messaging.contacts.core.domain.ContactTypes.ContactSort;
import com.messaging.contacts.core.exception.ContactCoreException;
import org.junit.jupiter.api.Test;

class ContactCursorCodecTests {

  private final ContactCursorCodec codec = new ContactCursorCodec("test-contact-cursor-secret");

  @Test
  void roundTripsLargeDecimalIdsWithoutPrecisionLoss() {
    long companyId = 922337203685470000L;
    long contactId = 922337203685470001L;

    String cursor =
        codec.encode(
            companyId,
            ContactSort.UPDATED_DESC,
            "filters",
            "2026-09-30T06:00:00Z",
            contactId);

    var decoded =
        codec.decode(cursor, companyId, ContactSort.UPDATED_DESC, "filters");

    assertThat(decoded.contactId()).isEqualTo(contactId);
    assertThat(decoded.sortValue()).isEqualTo("2026-09-30T06:00:00Z");
  }

  @Test
  void rejectsTamperingAndCrossTenantOrFilterReuse() {
    String cursor =
        codec.encode(10L, ContactSort.NAME_ASC, "filter-a", "alice", 20L);

    assertThatThrownBy(
            () -> codec.decode(cursor + "x", 10L, ContactSort.NAME_ASC, "filter-a"))
        .isInstanceOf(ContactCoreException.class)
        .hasMessage(ContactCoreException.INVALID_CURSOR);
    assertThatThrownBy(
            () -> codec.decode(cursor, 11L, ContactSort.NAME_ASC, "filter-a"))
        .isInstanceOf(ContactCoreException.class)
        .hasMessage(ContactCoreException.INVALID_CURSOR);
    assertThatThrownBy(
            () -> codec.decode(cursor, 10L, ContactSort.NAME_ASC, "filter-b"))
        .isInstanceOf(ContactCoreException.class)
        .hasMessage(ContactCoreException.INVALID_CURSOR);
  }
}
