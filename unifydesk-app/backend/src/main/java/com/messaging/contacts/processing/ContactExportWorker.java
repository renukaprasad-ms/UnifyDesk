package com.messaging.contacts.processing;

import com.messaging.asyncjob.service.AsyncJobService;
import com.messaging.contacts.audience.ContactAudienceProperties;
import com.messaging.contacts.processing.ContactExportRowSource.*;
import com.messaging.storage.enums.StorageUsageType;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.*;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ContactExportWorker {

  private final ContactExportRepository repository;
  private final ContactExportRowSource rowSource;
  private final ContactExportService exportService;
  private final ContactStorageAdapter storage;
  private final ContactAudienceProperties properties;
  private final AsyncJobService asyncJobs;

  public void process(long companyId, long exportId, long jobId) {
    ContactExportRepository.ExportRow export = repository.require(companyId, exportId);
    repository.markRunning(companyId, exportId);
    asyncJobs.startOrResume(jobId, 0, "Export running");
    Source source =
        new Source(SourceType.valueOf(export.sourceType()), export.sourceId());
    List<String> fields = exportService.parseFields(export.fieldsJson());

    Path temp = null;
    try {
      temp = Files.createTempFile("contacts-export-", ".csv");
      long rows = 0;
      long cursor = 0;
      try (BufferedWriter writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8);
          CSVPrinter printer =
              new CSVPrinter(writer, CSVFormat.DEFAULT.builder().setHeader(fields.toArray(String[]::new)).get())) {
        while (true) {
          List<ExportRow> page =
              rowSource.page(companyId, source, fields, cursor, properties.boundedBatchSize());
          if (page.isEmpty()) break;
          for (ExportRow row : page) {
            Object[] values =
                fields.stream()
                    .map(field -> CsvSafety.neutralizeFormula(row.values().get(field)))
                    .toArray();
            printer.printRecord(values);
            rows++;
            cursor = row.contactId();
          }
          printer.flush();
          asyncJobs.incrementProgress(jobId, page.size(), "Export running");
        }
      }
      long size = Files.size(temp);
      String objectKey = "contacts/" + companyId + "/exports/" + exportId + "/contacts.csv";
      ContactStorageAdapter.PublishedObject published =
          storage.publishFile(
              companyId,
              StorageUsageType.EXPORT,
              "CONTACT_EXPORT",
              Long.toString(exportId),
              temp,
              size,
              objectKey);
      repository.complete(companyId, exportId, rows, published);
      asyncJobs.complete(jobId, "Export completed");
    } catch (RuntimeException | IOException exception) {
      repository.fail(companyId, exportId);
      asyncJobs.fail(jobId, exception.getClass().getSimpleName());
      throw exception instanceof RuntimeException runtime
          ? runtime
          : new UncheckedIOException((IOException) exception);
    } finally {
      if (temp != null) {
        try {
          Files.deleteIfExists(temp);
        } catch (IOException ignored) {
          // Temporary OS file cleanup failure is non-fatal; no PII is logged.
        }
      }
    }
  }
}
