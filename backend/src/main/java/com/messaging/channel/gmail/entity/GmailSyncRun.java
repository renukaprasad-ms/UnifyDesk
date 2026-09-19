package com.messaging.channel.gmail.entity;

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

  @Column(name = "connection_id", nullable = false)
  private Long connectionId;

  @Column(name = "from_date", nullable = false)
  private LocalDate fromDate;

  @Column(name = "to_date", nullable = false)
  private LocalDate toDate;

  @Column(name = "discovery_complete", nullable = false)
  private boolean discoveryComplete;

  @Column(name = "discovered_count", nullable = false)
  private long discoveredCount;

  @Column(name = "existing_count", nullable = false)
  private long existingCount;

  @Column(name = "batch_count", nullable = false)
  private int batchCount;

  @Column(name = "failed_count", nullable = false)
  private long failedCount;

  @Column(name = "history_id", length = 128)
  private String historyId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void prePersist() {
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = Instant.now();
  }
}
