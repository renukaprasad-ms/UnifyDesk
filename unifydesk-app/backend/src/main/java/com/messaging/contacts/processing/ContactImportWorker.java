package com.messaging.contacts.processing;

import com.messaging.asyncjob.service.AsyncJobService;
import com.messaging.contacts.audience.ContactAudienceProperties;
import com.messaging.contacts.processing.ContactImportMutationPort.*;
import com.messaging.contacts.processing.ContactImportRepository.ImportRow;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ContactImportWorker {

  private final ContactImportRepository repository;
  private final ContactImportService importService;
  private final ContactStorageAdapter storage;
  private final ContactAudienceProperties properties;
  private final AsyncJobService asyncJobs;
  private final ObjectProvider<ContactImportMutationPort> mutationPortProvider;

  public void process(long companyId, long importId, long jobId) {
    ContactImportMutationPort mutationPort = mutationPortProvider.getIfAvailable();
    if (mutationPort == null) {
      throw new IllegalStateException(
          "CONTACT_IMPORT_CORE_ADAPTER_NOT_INTEGRATED");
    }
    ImportRow row = repository.start(companyId, importId);
    ImportMapping mapping = importService.parseMapping(row.mappingJson());
    DuplicateStrategy duplicateStrategy = DuplicateStrategy.valueOf(row.duplicateStrategy());
    asyncJobs.startOrResume(jobId, Math.max(row.totalRows(), row.processedRows()), "Import running");

    long cursor = row.processedRowCursor();
    long total = cursor;
    List<ContactImportMutationPort.ImportRow> batch =
        new ArrayList<>(properties.boundedBatchSize());
    try (InputStream input = storage.openStream(importService.ref(row));
        Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8);
        CSVParser parser =
            CSVFormat.DEFAULT
                .builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(false)
                .get()
                .parse(reader)) {
      if (parser.getHeaderNames().size() > properties.getCsvMaxColumns()) {
        throw new IllegalStateException("CONTACT_IMPORT_INVALID");
      }
      for (CSVRecord record : parser) {
        long rowNumber = record.getRecordNumber();
        total = rowNumber;
        if (rowNumber <= cursor) continue;
        if (rowNumber > properties.getCsvMaxRows() || record.size() > properties.getCsvMaxColumns()) {
          throw new IllegalStateException("CONTACT_IMPORT_INVALID");
        }
        List<String> cells = new ArrayList<>(record.size());
        for (String value : record) {
          CsvSafety.validateCell(value, properties.getCsvMaxCellChars());
          cells.add(value);
        }
        batch.add(new ContactImportMutationPort.ImportRow(rowNumber, cells));
        if (batch.size() >= properties.boundedBatchSize()) {
          applyBatch(row, mapping, duplicateStrategy, batch, jobId, mutationPort);
          batch.clear();
        }
      }
      if (!batch.isEmpty()) applyBatch(row, mapping, duplicateStrategy, batch, jobId, mutationPort);
      repository.complete(companyId, importId, total);
      asyncJobs.complete(jobId, "Import completed");
    } catch (RuntimeException | IOException exception) {
      repository.fail(companyId, importId);
      asyncJobs.fail(jobId, exception.getClass().getSimpleName());
      throw exception instanceof RuntimeException runtime
          ? runtime
          : new UncheckedIOException((IOException) exception);
    }
  }

  private void applyBatch(
      ImportRow importRow,
      ImportMapping mapping,
      DuplicateStrategy duplicateStrategy,
      List<ContactImportMutationPort.ImportRow> batch,
      long jobId,
      ContactImportMutationPort port) {
    BatchResult result =
        port.applyBatch(
            importRow.companyId(),
            importRow.requestedByUserId(),
            mapping,
            duplicateStrategy,
            importRow.targetListId(),
            List.copyOf(batch));
    long failed = result.failures() == null ? 0 : result.failures().size();
    long succeeded = result.succeeded();
    long lastRow = batch.getLast().rowNumber();
    repository.advance(
        importRow.companyId(), importRow.id(), lastRow, batch.size(), succeeded, failed);
    asyncJobs.incrementProgress(jobId, batch.size(), "Import running");
  }
}
