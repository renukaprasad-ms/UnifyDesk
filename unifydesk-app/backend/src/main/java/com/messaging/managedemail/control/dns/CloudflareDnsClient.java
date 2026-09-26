package com.messaging.managedemail.control.dns;

import com.messaging.managedemail.control.enums.ManagedEmailEnums.DnsRecordType;
import java.util.List;

public interface CloudflareDnsClient {

  List<Record> find(DnsRecordType type, String name);

  void create(DnsRecordType type, String name, String content, Integer priority, boolean proxied);

  void update(
      String id,
      DnsRecordType type,
      String name,
      String content,
      Integer priority,
      boolean proxied);

  void delete(String id);

  record Record(String id, String content, Boolean proxied) {}
}
