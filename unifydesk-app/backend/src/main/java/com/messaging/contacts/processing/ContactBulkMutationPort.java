package com.messaging.contacts.processing;

import java.util.List;

public interface ContactBulkMutationPort {

  BatchResult apply(
      long companyId, long requestedByUserId, Operation operation, List<Long> contactIds);

  record Operation(String type, Long targetId, String fieldKey, String valueJson) {}

  record BatchResult(long succeeded, long failed) {}
}
