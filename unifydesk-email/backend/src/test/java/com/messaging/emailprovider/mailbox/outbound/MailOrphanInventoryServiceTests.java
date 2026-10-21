package com.messaging.emailprovider.mailbox.outbound;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

class MailOrphanInventoryServiceTests {
  @Test
  void onlyServerGeneratedOpaqueMimeKeysAreEligible() {
    assertTrue(MailOrphanInventoryService.eligibleName(
        "123e4567-e89b-12d3-a456-426614174000.eml"));
    assertFalse(MailOrphanInventoryService.eligibleName("../../secrets"));
    assertFalse(MailOrphanInventoryService.eligibleName("receiving-abc.part"));
    assertFalse(MailOrphanInventoryService.eligibleName(
        "123e4567-e89b-12d3-a456-426614174000.eml/../evil"));
  }

  @Test
  void protectsAllRecentFilesEvenWhenTheyLookUnreferenced() {
    Instant now=Instant.parse("2026-10-09T10:00:00Z");
    assertFalse(MailOrphanInventoryService.oldEnough(
        now.minus(71,ChronoUnit.HOURS),now));
    assertTrue(MailOrphanInventoryService.oldEnough(
        now.minus(73,ChronoUnit.HOURS),now));
    assertFalse(MailOrphanInventoryService.oldEnough(
        now.plus(1,ChronoUnit.HOURS),now));
  }
}
