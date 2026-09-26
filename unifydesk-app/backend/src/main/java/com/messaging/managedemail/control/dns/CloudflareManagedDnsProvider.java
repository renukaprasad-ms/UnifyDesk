package com.messaging.managedemail.control.dns;

import com.messaging.common.exception.ServiceUnavailableException;
import com.messaging.managedemail.control.config.ManagedEmailProperties;
import com.messaging.managedemail.control.enums.ManagedEmailEnums.DnsRecordType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CloudflareManagedDnsProvider implements ManagedDnsProvider {

  private final ManagedEmailProperties properties;
  private final CloudflareDnsClient client;

  @Override
  public boolean available() {
    return properties.hasCloudflareConfiguration();
  }

  @Override
  public void ensure(DnsRecord record) {
    requireAvailable();
    List<CloudflareDnsClient.Record> existing = client.find(record.type(), record.name());
    CloudflareDnsClient.Record exact =
        existing.stream()
            .filter(
                item ->
                    item.content().equals(record.value())
                        && (!isCname(record.type()) || Boolean.FALSE.equals(item.proxied())))
            .findFirst()
            .orElse(null);
    if (exact != null) return;

    boolean proxied = false;
    if (existing.isEmpty()) {
      client.create(
          record.type(), record.name(), record.value(), record.priority(), proxied);
    } else {
      client.update(
          existing.getFirst().id(),
          record.type(),
          record.name(),
          record.value(),
          record.priority(),
          proxied);
    }
  }

  @Override
  public void deleteExact(List<DnsRecord> records) {
    requireAvailable();
    for (DnsRecord desired : records) {
      for (CloudflareDnsClient.Record existing : client.find(desired.type(), desired.name())) {
        if (existing.content().equals(desired.value())) {
          client.delete(existing.id());
        }
      }
    }
  }

  private boolean isCname(DnsRecordType type) {
    return type == DnsRecordType.CNAME;
  }

  private void requireAvailable() {
    if (!available()) {
      throw new ServiceUnavailableException("Managed Email hosted DNS is not configured");
    }
  }
}
