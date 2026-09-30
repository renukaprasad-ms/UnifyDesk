package com.messaging.contacts.processing;

import com.messaging.contacts.consent.ConsentTypes.Channel;
import com.messaging.contacts.consent.ConsentTypes.PreferenceSource;
import java.util.List;
import java.util.Map;

public interface ContactImportMutationPort {

  BatchResult applyBatch(
      long companyId,
      long importId,
      long requestedByUserId,
      ImportMapping mapping,
      DuplicateStrategy duplicateStrategy,
      Long targetListId,
      List<ImportRow> rows);

  enum DuplicateStrategy {
    SKIP,
    MERGE_EXACT,
    FAIL
  }

  record ImportMapping(
      Integer displayNameColumn,
      Integer firstNameColumn,
      Integer lastNameColumn,
      Integer organizationColumn,
      Integer notesColumn,
      Integer emailColumn,
      Integer phoneColumn,
      Integer tagNamesColumn,
      Map<Long, Integer> customFieldColumns,
      List<ConsentColumnMapping> consentMappings,
      String defaultCountryCode) {}

  record ConsentColumnMapping(
      Channel channel,
      int stateColumn,
      PreferenceSource source,
      Integer evidenceColumn,
      Integer occurredAtColumn) {}

  record ImportRow(long rowNumber, List<String> cells) {}

  record RowFailure(long rowNumber, String code, String safeMessage) {}

  record BatchResult(long succeeded, List<RowFailure> failures) {}
}
