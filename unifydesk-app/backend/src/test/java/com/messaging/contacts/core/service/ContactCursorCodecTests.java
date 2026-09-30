package com.messaging.contacts.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.messaging.contacts.core.domain.ContactTypes.ContactSort;
import com.messaging.contacts.core.exception.ContactCoreException;
import org.junit.jupiter.api.Test;

class ContactCursorCodecTests {

  private static final String SHARED_KEY = "test-contact-cursor-secret";
  private static final String OTHER_KEY = "different-contact-cursor-secret";

  @Test
  void roundTripsLargeDecimalIdsWithoutPrecisionLoss() {
    ContactCursorCodec codec = codec(SHARED_KEY);
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
  void separateInstancesUsingSameConfiguredKeyAcceptEachOthersCursors() {
    ContactCursorCodec firstReplica = codec(SHARED_KEY);
    ContactCursorCodec secondReplica = codec(SHARED_KEY);

    String cursor =
        firstReplica.encode(10L, ContactSort.UPDATED_DESC, "filter-a", "updated", 20L);

    assertThat(
            secondReplica.decode(
                cursor, 10L, ContactSort.UPDATED_DESC, "filter-a"))
        .isEqualTo(new ContactCursorCodec.Decoded("updated", 20L));
  }

  @Test
  void cursorRemainsValidAfterSimulatedRestartWithSameKey() {
    String cursor =
        codec(SHARED_KEY)
            .encode(10L, ContactSort.CREATED_DESC, "filter-a", "created", 20L);

    ContactCursorCodec restartedCodec = codec(SHARED_KEY);

    assertThat(
            restartedCodec.decode(
                cursor, 10L, ContactSort.CREATED_DESC, "filter-a"))
        .isEqualTo(new ContactCursorCodec.Decoded("created", 20L));
  }

  @Test
  void differentConfiguredKeysRejectEachOthersCursors() {
    String cursor =
        codec(SHARED_KEY)
            .encode(10L, ContactSort.NAME_ASC, "filter-a", "alice", 20L);

    assertInvalidCursor(
        () -> codec(OTHER_KEY).decode(cursor, 10L, ContactSort.NAME_ASC, "filter-a"));
  }

  @Test
  void rejectsTamperingAndCrossTenantSortOrFilterReuse() {
    ContactCursorCodec codec = codec(SHARED_KEY);
    String cursor =
        codec.encode(10L, ContactSort.NAME_ASC, "filter-a", "alice", 20L);

    assertInvalidCursor(
        () -> codec.decode(cursor + "x", 10L, ContactSort.NAME_ASC, "filter-a"));
    assertInvalidCursor(
        () -> codec.decode(cursor, 11L, ContactSort.NAME_ASC, "filter-a"));
    assertInvalidCursor(
        () -> codec.decode(cursor, 10L, ContactSort.UPDATED_DESC, "filter-a"));
    assertInvalidCursor(
        () -> codec.decode(cursor, 10L, ContactSort.NAME_ASC, "filter-b"));
  }

  @Test
  void dedicatedKeyTakesPrecedenceOverSharedPlatformFallback() {
    ContactCursorCodec dedicated =
        new ContactCursorCodec(SHARED_KEY, OTHER_KEY);
    ContactCursorCodec sameDedicated =
        new ContactCursorCodec(SHARED_KEY, "another-shared-platform-secret");
    ContactCursorCodec fallbackOnly =
        new ContactCursorCodec("", OTHER_KEY);

    String cursor =
        dedicated.encode(10L, ContactSort.NAME_ASC, "filter-a", "alice", 20L);

    assertThat(
            sameDedicated.decode(
                cursor, 10L, ContactSort.NAME_ASC, "filter-a"))
        .isEqualTo(new ContactCursorCodec.Decoded("alice", 20L));
    assertInvalidCursor(
        () -> fallbackOnly.decode(cursor, 10L, ContactSort.NAME_ASC, "filter-a"));
  }

  @Test
  void sharedPlatformKeyIsStableFallbackWhenDedicatedKeyIsMissing() {
    ContactCursorCodec firstReplica = new ContactCursorCodec("", SHARED_KEY);
    ContactCursorCodec secondReplica = new ContactCursorCodec(null, SHARED_KEY);

    String cursor =
        firstReplica.encode(10L, ContactSort.NAME_ASC, "filter-a", "alice", 20L);

    assertThat(
            secondReplica.decode(
                cursor, 10L, ContactSort.NAME_ASC, "filter-a"))
        .isEqualTo(new ContactCursorCodec.Decoded("alice", 20L));
  }

  @Test
  void missingStableSecretFailsConfigurationWithoutExposingSecretMaterial() {
    String dedicatedSecret = "dedicated-secret-that-must-not-leak";
    String sharedSecret = "shared-secret-that-must-not-leak";

    IllegalStateException blankSecretFailure =
        assertThrows(IllegalStateException.class, () -> new ContactCursorCodec("", "   "));
    IllegalStateException missingSecretFailure =
        assertThrows(IllegalStateException.class, () -> new ContactCursorCodec(null, null));

    assertThat(blankSecretFailure.getMessage())
        .contains("APP_CONTACTS_CURSOR_HMAC_KEY", "CHANNEL_CREDENTIAL_MASTER_KEY")
        .doesNotContain(dedicatedSecret, sharedSecret);
    assertThat(missingSecretFailure.getMessage())
        .contains("APP_CONTACTS_CURSOR_HMAC_KEY", "CHANNEL_CREDENTIAL_MASTER_KEY")
        .doesNotContain(dedicatedSecret, sharedSecret);
  }

  private ContactCursorCodec codec(String key) {
    return new ContactCursorCodec(key, "");
  }

  private void assertInvalidCursor(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
    assertThatThrownBy(action)
        .isInstanceOf(ContactCoreException.class)
        .hasMessage(ContactCoreException.INVALID_CURSOR);
  }
}
