package com.messaging.emailprovider.dns;

import com.messaging.emailprovider.dkim.entity.DkimKey;
import com.messaging.emailprovider.domain.dto.DnsRecordResponse;
import com.messaging.emailprovider.domain.entity.SendingDomain;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DomainDnsRecordService {

  public List<DnsRecordResponse> recordsFor(SendingDomain domain, DkimKey key) {
    return List.of(
        new DnsRecordResponse(
            DnsRecordType.TXT,
            ownershipRecordName(domain.getDomainName()),
            ownershipRecordValue(domain.getVerificationToken()),
            DnsRecordPurpose.DOMAIN_OWNERSHIP,
            true,
            domain.getOwnershipVerifiedAt() != null),
        new DnsRecordResponse(
            DnsRecordType.TXT,
            dkimRecordName(key.getSelector(), domain.getDomainName()),
            dkimRecordValue(key.getPublicKey()),
            DnsRecordPurpose.DKIM,
            true,
            domain.getDkimVerifiedAt() != null));
  }

  public String ownershipRecordName(String domain) {
    return "_reachlyo-verification." + domain;
  }

  public String ownershipRecordValue(String token) {
    return "reachlyo-verification=" + token;
  }

  public String dkimRecordName(String selector, String domain) {
    return selector + "._domainkey." + domain;
  }

  public String dkimRecordValue(String publicKey) {
    return "v=DKIM1; k=rsa; p=" + publicKey;
  }
}
