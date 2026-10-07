package com.messaging.contacts.processing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.asyncjob.enums.AsyncJobType;
import com.messaging.asyncjob.service.AsyncJobService;
import com.messaging.common.exception.ForbiddenException;
import com.messaging.common.exception.NotFoundException;
import com.messaging.company.service.CompanyAuthorizationService;
import com.messaging.contacts.audience.ContactAudiencePermissions;
import com.messaging.contacts.processing.ContactImportMutationPort.ImportMapping;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ContactProcessingAuthorizationPolicy {

  public static final String AUTHORIZATION_REVOKED = "CONTACT_ASYNC_AUTHORIZATION_REVOKED";
  public static final String RESOURCE_INVALID = "CONTACT_ASYNC_RESOURCE_INVALID";

  private final CompanyAuthorizationService authorization;
  private final ContactImportRepository importRepository;
  private final ContactExportRepository exportRepository;
  private final ContactBulkRepository bulkRepository;
  private final ObjectMapper objectMapper;

  public void requireBulkQueue(
      long userId, long companyId, ContactBulkMutationPort.Operation operation) {
    if (operation == null || operation.type() == null) {
      throw new com.messaging.common.exception.BadRequestException("CONTACT_BULK_INVALID");
    }
    requireCurrentPermission(
        userId,
        companyId,
        "DELETE".equals(operation.type())
            ? ContactAudiencePermissions.DELETE
            : ContactAudiencePermissions.MODIFY);
  }

  public void requireImportCommit(
      long userId, long companyId, ImportMapping mapping) {
    requireCurrentPermission(userId, companyId, ContactAudiencePermissions.IMPORT);
    if (hasConsentMappings(mapping)) {
      authorization.requirePermission(
          userId, companyId, ContactAudiencePermissions.CONSENT_MODIFY);
    }
  }

  public Decision authorizeClaim(
      AsyncJobType type, AsyncJobService.ClaimedAsyncJob job) {
    if (type == AsyncJobType.CONTACT_DELETE_PURGE) {
      return Decision.permitted();
    }
    if (job == null
        || job.companyId() == null
        || job.userId() == null
        || job.resourceId() == null) {
      return Decision.denied(RESOURCE_INVALID);
    }

    try {
      long resourceId = parseResourceId(job.resourceId());
      authorization.requireActiveMembership(job.userId(), job.companyId());

      return switch (type) {
        case CONTACT_IMPORT -> authorizeImport(job, resourceId);
        case CONTACT_EXPORT -> authorizeExport(job, resourceId);
        case CONTACT_BULK -> authorizeBulk(job, resourceId);
        case CONTACT_SEGMENT_COUNT -> {
          authorization.requirePermission(
              job.userId(), job.companyId(), ContactAudiencePermissions.READ);
          yield Decision.permitted();
        }
        case CONTACT_AUDIENCE_SNAPSHOT -> {
          authorization.requirePermission(
              job.userId(), job.companyId(), ContactAudiencePermissions.READ);
          yield Decision.permitted();
        }
        default -> Decision.denied(RESOURCE_INVALID);
      };
    } catch (ForbiddenException exception) {
      return Decision.denied(AUTHORIZATION_REVOKED);
    } catch (NotFoundException | IllegalArgumentException | IllegalStateException exception) {
      return Decision.denied(RESOURCE_INVALID);
    }
  }

  private Decision authorizeImport(
      AsyncJobService.ClaimedAsyncJob job, long importId) {
    ContactImportRepository.ImportRow row =
        importRepository.require(job.companyId(), importId);
    requireInitiatingUser(job, row.requestedByUserId());
    authorization.requirePermission(
        job.userId(), job.companyId(), ContactAudiencePermissions.IMPORT);
    if (hasConsentMappings(parseImportMapping(row.mappingJson()))) {
      authorization.requirePermission(
          job.userId(), job.companyId(), ContactAudiencePermissions.CONSENT_MODIFY);
    }
    return Decision.permitted();
  }

  private Decision authorizeExport(
      AsyncJobService.ClaimedAsyncJob job, long exportId) {
    ContactExportRepository.ExportRow row =
        exportRepository.require(job.companyId(), exportId);
    requireInitiatingUser(job, row.requestedByUserId());
    authorization.requirePermission(
        job.userId(), job.companyId(), ContactAudiencePermissions.EXPORT);
    return Decision.permitted();
  }

  private Decision authorizeBulk(
      AsyncJobService.ClaimedAsyncJob job, long operationId) {
    ContactBulkRepository.BulkRow row =
        bulkRepository.require(job.companyId(), operationId);
    requireInitiatingUser(job, row.requestedByUserId());
    String permission =
        switch (row.operationType()) {
          case "DELETE" -> ContactAudiencePermissions.DELETE;
          case "ADD_TAG", "REMOVE_TAG", "ADD_TO_LIST", "REMOVE_FROM_LIST", "UPDATE_FIELD" ->
              ContactAudiencePermissions.MODIFY;
          default -> throw new IllegalStateException("Stored bulk operation type is invalid");
        };
    authorization.requirePermission(job.userId(), job.companyId(), permission);
    return Decision.permitted();
  }

  private void requireCurrentPermission(
      long userId, long companyId, String permission) {
    authorization.requireActiveMembership(userId, companyId);
    authorization.requirePermission(userId, companyId, permission);
  }

  private ImportMapping parseImportMapping(String json) {
    if (json == null || json.isBlank()) {
      throw new IllegalStateException("Stored import mapping is missing");
    }
    try {
      return objectMapper.readValue(json, ImportMapping.class);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Stored import mapping is invalid", exception);
    }
  }

  private boolean hasConsentMappings(ImportMapping mapping) {
    if (mapping == null) return false;
    boolean hasColumnMappings =
        mapping.consentMappings() != null && !mapping.consentMappings().isEmpty();
    boolean hasDeclarations =
        mapping.consentDeclarations() != null && !mapping.consentDeclarations().isEmpty();
    return hasColumnMappings || hasDeclarations;
  }

  private long parseResourceId(String resourceId) {
    long parsed = Long.parseLong(resourceId);
    if (parsed <= 0) throw new IllegalArgumentException("Invalid resource id");
    return parsed;
  }

  private void requireInitiatingUser(
      AsyncJobService.ClaimedAsyncJob job, long requestedByUserId) {
    if (requestedByUserId != job.userId()) {
      throw new IllegalStateException("Async job actor does not match resource requester");
    }
  }

  public record Decision(boolean allowed, String failureCode) {
    public static Decision permitted() {
      return new Decision(true, null);
    }

    public static Decision denied(String failureCode) {
      return new Decision(false, failureCode);
    }
  }
}
