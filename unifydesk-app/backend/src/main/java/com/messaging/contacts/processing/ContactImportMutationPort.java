package com.messaging.contacts.processing;

import java.util.List;

public interface ContactImportMutationPort {

  BatchResult applyBatch(
      long companyId, long requestedByUserId, ImportMapping mapping, DuplicateStrategy duplicateStrategy,
      Long targetListId, List<ImportRow> rows);

  enum DuplicateStrategy {
    SKIP,
    MERGE_EXACT,
    FAIL
  }

  record ImportMapping(
      Integer emailColumn,
      Integer phoneColumn,
      Integer firstNameColumn,
      Integer lastNameColumn,
      java.util.Map<Long, Integer> customFieldColumns,
      Integer consentColumn,
      String defaultCountryCode) {}

  record ImportRow(long rowNumber, List<String> cells) {}

  record RowFailure(long rowNumber, String code, String safeMessage) {}

  record BatchResult(long succeeded, List<RowFailure> failures) {}
}
