package com.messaging.contacts.core.domain;

import com.messaging.contacts.core.domain.ContactTypes.ContactListStatus;
import com.messaging.contacts.core.domain.ContactTypes.ContactStatus;
import com.messaging.contacts.core.domain.ContactTypes.CustomFieldType;
import com.messaging.contacts.core.domain.ContactTypes.DefinitionStatus;
import com.messaging.contacts.core.domain.ContactTypes.IdentityStatus;
import com.messaging.contacts.core.domain.ContactTypes.IdentityType;
import com.messaging.contacts.consent.ConsentTypes.PreferenceState;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class CoreRows {

  private CoreRows() {}

  public record ContactRow(
      long id,
      long companyId,
      String firstName,
      String lastName,
      String displayName,
      String organization,
      String jobTitle,
      String countryCode,
      String timezone,
      String language,
      String source,
      String notes,
      Long ownerUserId,
      ContactStatus status,
      Instant lastActivityAt,
      Long mergedIntoContactId,
      long createdByUserId,
      Instant createdAt,
      Instant updatedAt,
      Instant deletedAt,
      long version) {}

  public record IdentityRow(
      long id,
      long companyId,
      long contactId,
      IdentityType type,
      String namespace,
      String value,
      String normalizedValue,
      boolean primary,
      IdentityStatus status,
      Instant createdAt,
      Instant updatedAt,
      Instant deletedAt) {}

  public record ContactSummaryRow(
      ContactRow contact,
      String primaryEmail,
      String primaryPhone,
      PreferenceState emailMarketingPreference,
      PreferenceState smsMarketingPreference,
      PreferenceState whatsappMarketingPreference) {}

  public record TagRow(
      long id,
      long companyId,
      String name,
      String normalizedName,
      String color,
      String metadataJson,
      Instant createdAt,
      Instant updatedAt,
      Instant archivedAt) {}

  public record CustomFieldDefinitionRow(
      long id,
      long companyId,
      String fieldKey,
      String label,
      String normalizedLabel,
      CustomFieldType type,
      String optionsJson,
      int position,
      DefinitionStatus status,
      Instant createdAt,
      Instant updatedAt) {}

  public record CustomFieldValueRow(
      long companyId,
      long contactId,
      long fieldId,
      String textValue,
      BigDecimal numberValue,
      Boolean booleanValue,
      LocalDate dateValue,
      Instant datetimeValue,
      List<String> textArrayValue,
      Instant updatedAt) {}

  public record ContactListRow(
      long id,
      long companyId,
      String name,
      String normalizedName,
      String description,
      ContactListStatus status,
      long memberCount,
      long createdByUserId,
      Instant createdAt,
      Instant updatedAt,
      Instant archivedAt,
      long version) {}
}
