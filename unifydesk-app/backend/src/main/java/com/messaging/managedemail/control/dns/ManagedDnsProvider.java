package com.messaging.managedemail.control.dns;

import com.messaging.managedemail.control.enums.ManagedEmailEnums.DnsRecordType;
import java.util.List;

public interface ManagedDnsProvider {

  boolean available();

  void ensure(DnsRecord record);

  void deleteExact(List<DnsRecord> records);

  record DnsRecord(DnsRecordType type, String name, String value, Integer priority) {}
}
