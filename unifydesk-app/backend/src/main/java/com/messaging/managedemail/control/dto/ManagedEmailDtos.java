package com.messaging.managedemail.control.dto;

import com.messaging.broadcast.enums.*;
import com.messaging.managedemail.control.enums.ManagedEmailEnums.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class ManagedEmailDtos {

  private ManagedEmailDtos() {}

  public record CustomerDomainRequest(
      @NotBlank @Size(max = 253) String domain,
      @NotBlank @Size(max = 200) String idempotencyKey) {}

  public record HostedDomainRequest(
      @NotBlank @Size(max = 200) String idempotencyKey,
      @NotBlank @Size(min = 2, max = 63) String desiredSubdomain) {}

  public record SubdomainAvailabilityResponse(
      boolean available,
      String subdomain,
      String domain,
      String message) {}

  public record CreateSenderRequest(
      @NotBlank String domainId,
      @NotBlank @Size(max = 64) String localPart,
      @Size(max = 255) String displayName,
      @Size(max = 320) String replyToAddress) {}

  public record OverviewResponse(
      boolean enabled,
      String serviceStatus,
      SendingStatus sendingStatus,
      String regionLabel,
      long domainLimit,
      long senderLimit,
      String globalNotice) {}

  public record DnsRecordResponse(
      String id,
      DnsRecordPurpose purpose,
      DnsRecordType type,
      String name,
      String value,
      Integer priority,
      boolean required,
      DnsRecordStatus status) {}

  public record DomainResponse(
      String id,
      DomainType type,
      String domain,
      DomainStatus status,
      VerificationStatus verificationStatus,
      DkimStatus dkimStatus,
      DmarcStatus dmarcStatus,
      String dmarcPolicy,
      List<DnsRecordResponse> dnsRecords,
      Instant activatedAt,
      Instant lastCheckedAt,
      String requestedSubdomain,
      String rejectionReason,
      boolean adminAssignedAlternative) {}

  public record SenderResponse(
      String id,
      String connectionId,
      String channelConnectionId,
      BroadcastSenderProvider provider,
      BroadcastSenderManagementMode managementMode,
      BroadcastSenderStatus status,
      String fromAddress,
      String displayName,
      String replyToAddress,
      String connectionName,
      String region,
      boolean productionAccessEnabled,
      boolean sendingEnabled,
      Double max24HourSend,
      Double maxSendRate,
      Double sentLast24Hours,
      Instant lastValidatedAt,
      String managedDomainId,
      String managedDomain) {}

  public record CreateResult<T>(T value, boolean replay) {}
}
