package com.messaging.managedemail.control.repository;

import com.messaging.managedemail.control.entity.ManagedEmailDnsRecord;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManagedEmailDnsRecordRepository
    extends JpaRepository<ManagedEmailDnsRecord, Long> {

  List<ManagedEmailDnsRecord> findByDomainIdAndCompanyIdOrderByCreatedAtAsc(
      Long domainId, Long companyId);

  Optional<ManagedEmailDnsRecord>
      findByDomainIdAndRecordPurposeAndRecordTypeAndRecordNameAndRecordValue(
          Long domainId,
          com.messaging.managedemail.control.enums.ManagedEmailEnums.DnsRecordPurpose purpose,
          com.messaging.managedemail.control.enums.ManagedEmailEnums.DnsRecordType type,
          String name,
          String value);
}
