package com.messaging.channel.gmail.entity;

import com.messaging.channel.email.EmailFolder;
import com.messaging.channel.email.EmailSyncMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "gmail_sync_runs")
public class GmailSyncRun {

  @Id
  @Column(name = "job_id")
  private Long jobId;

  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "connection_id", nullable = false)
  private Long connectionId;

  @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
  @Column(name = "sync_mode", nullable = false, length = 32)
  private EmailSyncMode syncMode;

  @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
  @Column(name = "folder_key", nullable = false, length = 32)
  private EmailFolder folder;

  @Column(name = "from_date")
  private LocalDate fromDate;

  @Column(name = "to_date")
  private LocalDate toDate;

  @Column(name = "message_count")
  private Integer messageCount;

  @Column(name = "discovery_complete", nullable = false)
  private boolean discoveryComplete;

  @Column(nullable = false)
  private boolean finalized;

  @Column(name = "discovered_count", nullable = false)
  private long discoveredCount;

  @Column(name = "existing_count", nullable = false)
  private long existingCount;

  @Column(name = "batch_count", nullable = false)
  private int batchCount;

  @Column(name = "failed_count", nullable = false)
  private long failedCount;

  @Column(name = "next_batch_index", nullable = false)
  private int nextBatchIndex;

  @Column(name = "next_page_token", columnDefinition = "TEXT")
  private String nextPageToken;

  @Column(name = "carry_ids_json", nullable = false, columnDefinition = "TEXT")
  private String carryIdsJson = "[]";

  @Column(name = "history_id", length = 128)
  private String historyId;

  @Column(name = "provider_scan_complete", nullable = false)
  private boolean providerScanComplete;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void prePersist() {
    Instant now = Instant.now();
    if (syncMode == null) syncMode = EmailSyncMode.DATE_RANGE;
    if (folder == null) folder = EmailFolder.INBOX;
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = Instant.now();
  }
}
