package com.messaging.contacts.core.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.messaging.contacts.core.domain.ContactTypes.ContactSort;
import com.messaging.contacts.core.domain.ContactTypes.ContactStatus;
import com.messaging.contacts.core.domain.ContactTypes.CoreField;
import com.messaging.contacts.core.domain.ContactTypes.IdentityType;
import com.messaging.contacts.consent.ConsentTypes.Channel;
import com.messaging.contacts.consent.ConsentTypes.PreferenceState;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class ContactDtos {

  private ContactDtos() {}

  public record IdentityInput(
      @NotNull IdentityType type,
      String namespace,
      @NotBlank @Size(max = 320) String value,
      Boolean primary,
      String countryContext) {}

  public record CreateContactRequest(
      @Size(max = 160) String firstName,
      @Size(max = 160) String lastName,
      @Size(max = 240) String displayName,
      @Size(max = 240) String organization,
      @Size(max = 160) String jobTitle,
      @Size(max = 2) String countryCode,
      @Size(max = 80) String timezone,
      @Size(max = 32) String language,
      @Size(max = 80) String source,
      @Size(max = 4000) String notes,
      String ownerUserId,
      @Valid @Size(max = 20) List<IdentityInput> identities,
      @Size(max = 100) List<String> tagIds,
      @Size(max = 100) Map<String, JsonNode> customFields) {}

  public record PatchContactRequest(
      @NotNull Long version,
      JsonNode firstName,
      JsonNode lastName,
      JsonNode displayName,
      JsonNode organization,
      JsonNode jobTitle,
      JsonNode countryCode,
      JsonNode timezone,
      JsonNode language,
      JsonNode source,
      JsonNode notes,
      JsonNode ownerUserId,
      @Size(max = 100) Map<String, JsonNode> customFields) {}

  public record CreateIdentityRequest(
      @NotNull IdentityType type,
      String namespace,
      @NotBlank @Size(max = 320) String value,
      Boolean primary,
      String countryContext) {}

  public enum MergeWinner {
    SOURCE,
    TARGET
  }

  public record MergeFieldStrategy(
      Map<CoreField, MergeWinner> coreFields,
      Map<String, MergeWinner> customFields) {}

  public record MergeRequest(
      @NotBlank String sourceContactId,
      @NotBlank String targetContactId,
      @NotNull Long sourceVersion,
      @NotNull Long targetVersion,
      MergeFieldStrategy fieldStrategy) {}

  public record TagRequest(
      @NotBlank @Size(max = 120) String name,
      @Size(max = 32) String color,
      JsonNode metadata) {}

  public enum CustomFieldApiType {
    TEXT,
    NUMBER,
    BOOLEAN,
    DATE,
    DATETIME,
    SINGLE_SELECT,
    MULTI_SELECT
  }

  public record CustomFieldDefinitionRequest(
      @NotBlank @Size(max = 64) String fieldKey,
      @NotBlank @Size(max = 120) String label,
      @NotNull CustomFieldApiType type,
      List<String> options,
      Integer position) {}

  public record CustomFieldDefinitionPatchRequest(
      @NotBlank @Size(max = 120) String label,
      List<String> options,
      Integer position) {}

  public record ContactListRequest(
      @NotBlank @Size(max = 160) String name,
      @Size(max = 1000) String description) {}

  public record AddListMembersRequest(@NotNull @Size(max = 500) List<String> contactIds) {}

  public record ContactPage<T>(List<T> items, String nextCursor, boolean hasMore) {}

  public record IdentityResponse(
      String id,
      IdentityType type,
      String namespace,
      String value,
      String normalizedValue,
      boolean primary,
      String status) {}

  public record TagResponse(
      String id,
      String name,
      String color,
      JsonNode metadata,
      Instant createdAt,
      Instant updatedAt) {}

  public record CustomFieldValueResponse(String fieldId, JsonNode value) {}

  public record ContactSummary(
      String id,
      String displayName,
      String firstName,
      String lastName,
      String organization,
      String primaryEmail,
      String primaryPhone,
      Map<Channel, PreferenceState> preferenceSummary,
      ContactStatus status,
      Instant updatedAt,
      long version) {}

  public record ContactDetail(
      String id,
      String companyId,
      String firstName,
      String lastName,
      String displayName,
      String organization,
      String primaryEmail,
      String primaryPhone,
      String jobTitle,
      String countryCode,
      String timezone,
      String language,
      String source,
      String notes,
      String ownerUserId,
      ContactStatus status,
      String mergedIntoContactId,
      List<IdentityResponse> identities,
      List<TagResponse> tags,
      List<ContactListResponse> lists,
      Map<String, JsonNode> customFields,
      Instant createdAt,
      Instant updatedAt,
      long version) {}

  public record CustomFieldDefinitionResponse(
      String id,
      String fieldKey,
      String label,
      CustomFieldApiType type,
      List<String> options,
      String indexability,
      String status,
      int position,
      Instant createdAt,
      Instant updatedAt) {}

  public record ContactListResponse(
      String id,
      String name,
      String description,
      String status,
      long memberCount,
      Instant createdAt,
      Instant updatedAt,
      long version) {}

  public record MergeResponse(
      String sourceContactId, String targetContactId, long targetVersion) {}

  public record DeleteResponse(String contactId, String status) {}

  public record SearchRequest(
      String cursor,
      int size,
      String q,
      ContactStatus status,
      Long tagId,
      Long listId,
      Instant createdAfter,
      Instant createdBefore,
      ContactSort sort) {}
}
