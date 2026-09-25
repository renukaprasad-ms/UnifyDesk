package com.messaging.emailprovider.domain.dto;

import com.messaging.emailprovider.dkim.entity.DkimKey;
import com.messaging.emailprovider.dns.DomainDnsRecordService;
import com.messaging.emailprovider.domain.entity.SendingDomain;
import com.messaging.emailprovider.domain.enums.SendingDomainStatus;
import java.time.Instant;
import java.util.List;

public record SendingDomainResponse(
    String id,
    String companyId,
    String domain,
    SendingDomainStatus status,
    boolean ownershipVerified,
    boolean dkimVerified,
    Instant verifiedAt,
    Instant lastCheckedAt,
    List<DnsRecordResponse> dnsRecords) {

  public static SendingDomainResponse from(
      SendingDomain domain, DkimKey key, DomainDnsRecordService recordService) {
    return new SendingDomainResponse(
        domain.getId().toString(),
        domain.getCompanyId().toString(),
        domain.getDomainName(),
        domain.getStatus(),
        domain.getOwnershipVerifiedAt() != null,
        domain.getDkimVerifiedAt() != null,
        domain.getVerifiedAt(),
        domain.getLastCheckedAt(),
        recordService.recordsFor(domain, key));
  }
}
