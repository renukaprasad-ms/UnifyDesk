package com.messaging.managedemail.control.service;

import com.messaging.managedemail.control.dto.ManagedEmailDtos.*;
import com.messaging.managedemail.control.entity.ManagedEmailDnsRecord;
import com.messaging.managedemail.control.entity.ManagedEmailDomain;
import com.messaging.managedemail.control.repository.ManagedEmailDnsRecordRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ManagedEmailResponseMapper {

  private final ManagedEmailDnsRecordRepository dnsRecordRepository;

  public DomainResponse domain(ManagedEmailDomain domain) {
    List<DnsRecordResponse> records =
        dnsRecordRepository
            .findByDomainIdAndCompanyIdOrderByCreatedAtAsc(domain.getId(), domain.getCompanyId())
            .stream()
            .map(this::dns)
            .toList();
    Instant lastChecked = latest(domain.getLastDnsCheckAt(), domain.getLastProviderSyncAt());
    return new DomainResponse(
        domain.getId().toString(),
        domain.getDomainType(),
        domain.getDisplayDomain(),
        domain.getStatus(),
        domain.getVerificationStatus(),
        domain.getDkimStatus(),
        domain.getDmarcStatus(),
        domain.getDmarcPolicy(),
        records,
        domain.getActivatedAt(),
        lastChecked,
        domain.getRequestedSubdomain(),
        domain.getRejectionReason(),
        domain.isAdminAssignedAlternative());
  }

  private DnsRecordResponse dns(ManagedEmailDnsRecord record) {
    return new DnsRecordResponse(
        record.getId().toString(),
        record.getRecordPurpose(),
        record.getRecordType(),
        record.getRecordName(),
        record.getRecordValue(),
        record.getPriority(),
        record.isRequired(),
        record.getStatus());
  }

  private Instant latest(Instant left, Instant right) {
    if (left == null) return right;
    if (right == null) return left;
    return left.isAfter(right) ? left : right;
  }
}
