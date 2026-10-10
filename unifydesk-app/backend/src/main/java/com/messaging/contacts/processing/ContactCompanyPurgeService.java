package com.messaging.contacts.processing;

import com.messaging.media.enums.StorageProviderType;
import com.messaging.media.provider.StorageProviderResolver;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Bounded company-level cleanup for Contacts-owned data.
 *
 * <p>Physical CSV artifacts are removed before their metadata rows. Database cleanup then proceeds
 * in FK-safe order. The caller invokes this method repeatedly until it returns true.
 */
@Service
@RequiredArgsConstructor
public class ContactCompanyPurgeService {

  private final JdbcTemplate jdbcTemplate;
  private final StorageProviderResolver storageProviderResolver;

  public boolean purgeCompanyBatch(long companyId, int requestedBatchSize) {
    int batchSize = Math.max(1, requestedBatchSize);

    if (!purgeImportArtifacts(companyId, batchSize)) return false;
    if (!purgeExportArtifacts(companyId, batchSize)) return false;

    if (!drain(
        "delete from contact_preference_events where ctid in "
            + "(select ctid from contact_preference_events where company_id = ? limit ?)",
        companyId,
        batchSize)) return false;
    if (!drain(
        "delete from contact_channel_preferences where ctid in "
            + "(select ctid from contact_channel_preferences where company_id = ? limit ?)",
        companyId,
        batchSize)) return false;
    if (!drain(
        "delete from contact_suppression_events where ctid in "
            + "(select ctid from contact_suppression_events where company_id = ? limit ?)",
        companyId,
        batchSize)) return false;
    if (!drain(
        "delete from contact_suppressions where ctid in "
            + "(select ctid from contact_suppressions where company_id = ? limit ?)",
        companyId,
        batchSize)) return false;
    if (!drain(
        "delete from contact_bulk_operations where ctid in "
            + "(select ctid from contact_bulk_operations where company_id = ? limit ?)",
        companyId,
        batchSize)) return false;
    if (!drain(
        "delete from contact_activity_events where ctid in "
            + "(select ctid from contact_activity_events where company_id = ? limit ?)",
        companyId,
        batchSize)) return false;

    // Snapshot membership contains immutable destinations and therefore PII. Snapshot headers can
    // remain as historical Broadcast provenance because they contain only source/version/counts.
    if (!drain(
        "delete from contact_audience_snapshot_members where ctid in "
            + "(select ctid from contact_audience_snapshot_members where company_id = ? limit ?)",
        companyId,
        batchSize)) return false;

    if (!drain(
        "delete from contact_custom_field_values where ctid in "
            + "(select ctid from contact_custom_field_values where company_id = ? limit ?)",
        companyId,
        batchSize)) return false;
    if (!drain(
        "delete from contact_tag_memberships where ctid in "
            + "(select ctid from contact_tag_memberships where company_id = ? limit ?)",
        companyId,
        batchSize)) return false;
    if (!drain(
        "delete from contact_list_memberships where ctid in "
            + "(select ctid from contact_list_memberships where company_id = ? limit ?)",
        companyId,
        batchSize)) return false;
    if (!drain(
        "delete from contact_identities where ctid in "
            + "(select ctid from contact_identities where company_id = ? limit ?)",
        companyId,
        batchSize)) return false;

    if (!drain(
        "delete from contact_merge_audit where ctid in "
            + "(select ctid from contact_merge_audit where company_id = ? limit ?)",
        companyId,
        batchSize)) return false;

    // CRM-owned records must be drained before deleting contacts and company membership.
    if (!drain(
        "delete from crm_tasks where ctid in (select ctid from crm_tasks where company_id = ? limit ?)",
        companyId, batchSize)) return false;
    if (!drain(
        "delete from crm_account_contacts where ctid in (select ctid from crm_account_contacts where company_id = ? limit ?)",
        companyId, batchSize)) return false;
    if (!drain(
        "delete from crm_accounts where ctid in (select ctid from crm_accounts where company_id = ? limit ?)",
        companyId, batchSize)) return false;
    if (!drain(
        "delete from crm_lead_stage_events where ctid in (select ctid from crm_lead_stage_events where company_id = ? limit ?)",
        companyId, batchSize)) return false;
    if (!drain(
        "delete from crm_leads where ctid in (select ctid from crm_leads where company_id = ? limit ?)",
        companyId, batchSize)) return false;

    if (!clearMergedReferences(companyId, batchSize)) return false;

    if (!drain(
        "delete from contacts where ctid in "
            + "(select ctid from contacts where company_id = ? limit ?)",
        companyId,
        batchSize)) return false;

    if (!drain(
        "delete from contact_tags where ctid in "
            + "(select ctid from contact_tags where company_id = ? limit ?)",
        companyId,
        batchSize)) return false;
    if (!drain(
        "delete from contact_custom_field_definitions where ctid in "
            + "(select ctid from contact_custom_field_definitions where company_id = ? limit ?)",
        companyId,
        batchSize)) return false;
    if (!drain(
        "delete from contact_lists where ctid in "
            + "(select ctid from contact_lists where company_id = ? limit ?)",
        companyId,
        batchSize)) return false;
    if (!drain(
        "delete from contact_segments where ctid in "
            + "(select ctid from contact_segments where company_id = ? limit ?)",
        companyId,
        batchSize)) return false;

    return true;
  }

  private boolean clearMergedReferences(long companyId, int batchSize) {
    int changed =
        jdbcTemplate.update(
            """
            update contacts
            set status = 'DELETED',
                merged_into_contact_id = null,
                deleted_at = coalesce(deleted_at, now()),
                updated_at = now(),
                version = version + 1
            where ctid in (
              select ctid
              from contacts
              where company_id = ?
                and merged_into_contact_id is not null
              order by id
              limit ?
            )
            """,
            companyId,
            batchSize);
    return changed < batchSize;
  }

  private boolean purgeImportArtifacts(long companyId, int batchSize) {
    List<ImportArtifact> rows =
        jdbcTemplate.query(
            """
            select id, storage_provider, storage_bucket, storage_object_key,
                   error_report_provider, error_report_bucket, error_report_object_key
            from contact_imports
            where company_id = ?
            order by id
            limit ?
            """,
            (rs, rowNum) ->
                new ImportArtifact(
                    rs.getLong("id"),
                    StorageProviderType.valueOf(rs.getString("storage_provider")),
                    rs.getString("storage_bucket"),
                    rs.getString("storage_object_key"),
                    nullableProvider(rs.getString("error_report_provider")),
                    rs.getString("error_report_bucket"),
                    rs.getString("error_report_object_key")),
            companyId,
            batchSize);

    for (ImportArtifact row : rows) {
      deleteObject(row.provider(), row.bucket(), row.objectKey());
      if (row.errorProvider() != null
          && row.errorBucket() != null
          && row.errorObjectKey() != null) {
        deleteObject(row.errorProvider(), row.errorBucket(), row.errorObjectKey());
      }
      jdbcTemplate.update(
          "delete from contact_imports where company_id = ? and id = ?", companyId, row.id());
    }
    return rows.size() < batchSize;
  }

  private boolean purgeExportArtifacts(long companyId, int batchSize) {
    List<ExportArtifact> rows =
        jdbcTemplate.query(
            """
            select id, output_provider, output_bucket, output_object_key
            from contact_exports
            where company_id = ?
            order by id
            limit ?
            """,
            (rs, rowNum) ->
                new ExportArtifact(
                    rs.getLong("id"),
                    nullableProvider(rs.getString("output_provider")),
                    rs.getString("output_bucket"),
                    rs.getString("output_object_key")),
            companyId,
            batchSize);

    for (ExportArtifact row : rows) {
      if (row.provider() != null && row.bucket() != null && row.objectKey() != null) {
        deleteObject(row.provider(), row.bucket(), row.objectKey());
      }
      jdbcTemplate.update(
          "delete from contact_exports where company_id = ? and id = ?", companyId, row.id());
    }
    return rows.size() < batchSize;
  }

  private void deleteObject(StorageProviderType provider, String bucket, String objectKey) {
    if (provider == null || bucket == null || objectKey == null) return;
    var storage = storageProviderResolver.resolve(provider);
    if (storage.getObjectMetadata(bucket, objectKey).exists()) {
      storage.delete(bucket, objectKey);
    }
  }

  private boolean drain(String sql, long companyId, int batchSize) {
    return jdbcTemplate.update(sql, companyId, batchSize) < batchSize;
  }

  private StorageProviderType nullableProvider(String value) {
    return value == null ? null : StorageProviderType.valueOf(value);
  }

  private record ImportArtifact(
      long id,
      StorageProviderType provider,
      String bucket,
      String objectKey,
      StorageProviderType errorProvider,
      String errorBucket,
      String errorObjectKey) {}

  private record ExportArtifact(
      long id, StorageProviderType provider, String bucket, String objectKey) {}
}
