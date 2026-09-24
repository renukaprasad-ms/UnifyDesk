package com.messaging.emailprovider.dns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.messaging.emailprovider.dkim.entity.DkimKey;
import com.messaging.emailprovider.domain.dto.DnsRecordResponse;
import com.messaging.emailprovider.domain.entity.SendingDomain;
import java.util.List;
import org.junit.jupiter.api.Test;

class DomainDnsRecordServiceTests {

  private final DomainDnsRecordService service = new DomainDnsRecordService();

  @Test
  void buildsOwnershipAndDkimRecords() {
    SendingDomain domain = new SendingDomain();
    domain.setDomainName("example.com");
    domain.setVerificationToken("token-123");

    DkimKey key = new DkimKey();
    key.setSelector("rly1");
    key.setPublicKey("PUBLICKEY");

    List<DnsRecordResponse> records = service.recordsFor(domain, key);

    assertEquals(2, records.size());

    DnsRecordResponse ownership = records.get(0);
    assertEquals("_reachlyo-verification.example.com", ownership.name());
    assertEquals("reachlyo-verification=token-123", ownership.value());
    assertEquals(DnsRecordPurpose.DOMAIN_OWNERSHIP, ownership.purpose());
    assertFalse(ownership.verified());

    DnsRecordResponse dkim = records.get(1);
    assertEquals("rly1._domainkey.example.com", dkim.name());
    assertEquals("v=DKIM1; k=rsa; p=PUBLICKEY", dkim.value());
    assertEquals(DnsRecordPurpose.DKIM, dkim.purpose());
    assertFalse(dkim.verified());
  }
}
