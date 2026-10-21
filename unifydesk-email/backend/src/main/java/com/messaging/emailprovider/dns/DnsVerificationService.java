package com.messaging.emailprovider.dns;

import com.messaging.emailprovider.dkim.entity.DkimKey;
import com.messaging.emailprovider.domain.entity.SendingDomain;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class DnsVerificationService {

  private final DnsResolver dnsResolver;
  private final DomainDnsRecordService recordService;

  public DnsVerificationService(DnsResolver dnsResolver, DomainDnsRecordService recordService) {
    this.dnsResolver = dnsResolver;
    this.recordService = recordService;
  }

  public DnsVerificationResult verify(SendingDomain domain, DkimKey key) {
    String ownershipExpected = recordService.ownershipRecordValue(domain.getVerificationToken());
    Set<String> ownershipRecords =
        dnsResolver.findTxtRecords(recordService.ownershipRecordName(domain.getDomainName()));

    String dkimExpected = recordService.dkimRecordValue(key.getPublicKey());
    Set<String> dkimRecords =
        dnsResolver.findTxtRecords(
            recordService.dkimRecordName(key.getSelector(), domain.getDomainName()));

    return new DnsVerificationResult(
        contains(ownershipRecords, ownershipExpected), contains(dkimRecords, dkimExpected));
  }

  private boolean contains(Set<String> records, String expected) {
    String normalizedExpected = JndiDnsResolver.normalizeTxtValue(expected);
    return records.stream()
        .map(JndiDnsResolver::normalizeTxtValue)
        .anyMatch(normalizedExpected::equals);
  }
}
