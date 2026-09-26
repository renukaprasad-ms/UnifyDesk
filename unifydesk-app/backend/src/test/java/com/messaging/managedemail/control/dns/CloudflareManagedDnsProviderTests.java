package com.messaging.managedemail.control.dns;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.messaging.managedemail.control.config.ManagedEmailProperties;
import com.messaging.managedemail.control.dns.CloudflareDnsClient.Record;
import com.messaging.managedemail.control.enums.ManagedEmailEnums.DnsRecordType;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CloudflareManagedDnsProviderTests {

  @Mock private CloudflareDnsClient client;

  private CloudflareManagedDnsProvider provider;

  @BeforeEach
  void setUp() {
    ManagedEmailProperties properties = new ManagedEmailProperties();
    properties.setCloudflareZoneId("zone");
    properties.setCloudflareApiToken("token");
    provider = new CloudflareManagedDnsProvider(properties, client);
  }

  @Test
  void exactUnproxiedCnameIsIdempotent() {
    var desired =
        new ManagedDnsProvider.DnsRecord(
            DnsRecordType.CNAME, "a._domainkey.example.com", "target.example", null);
    when(client.find(desired.type(), desired.name()))
        .thenReturn(List.of(new Record("1", desired.value(), false)));

    provider.ensure(desired);

    verify(client, never()).create(any(), any(), any(), any(), eq(false));
    verify(client, never()).update(any(), any(), any(), any(), any(), eq(false));
  }

  @Test
  void proxiedCnameIsCorrectedToDnsOnly() {
    var desired =
        new ManagedDnsProvider.DnsRecord(
            DnsRecordType.CNAME, "a._domainkey.example.com", "target.example", null);
    when(client.find(desired.type(), desired.name()))
        .thenReturn(List.of(new Record("record-1", desired.value(), true)));

    provider.ensure(desired);

    verify(client)
        .update(
            "record-1",
            DnsRecordType.CNAME,
            desired.name(),
            desired.value(),
            null,
            false);
  }

  @Test
  void cleanupDeletesOnlyExactManagedValues() {
    var desired =
        new ManagedDnsProvider.DnsRecord(
            DnsRecordType.TXT, "_dmarc.example.com", "v=DMARC1; p=quarantine", null);
    when(client.find(desired.type(), desired.name()))
        .thenReturn(
            List.of(
                new Record("managed", desired.value(), null),
                new Record("foreign", "v=DMARC1; p=none", null)));

    provider.deleteExact(List.of(desired));

    verify(client).delete("managed");
    verify(client, never()).delete("foreign");
  }
}
