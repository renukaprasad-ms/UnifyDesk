package com.messaging.channel.microsoft.entity;

import com.messaging.channel.email.EmailFolder;
import com.messaging.channel.email.EmailSyncMode;
import com.messaging.channel.microsoft.enums.MicrosoftBackfillStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
    name = "microsoft_backfill_runs",
    indexes =
        @Index(
            name = "idx_microsoft_backfill_claim",
            columnList = "status,next_attempt_at,connection_id,updated_at"))
public class MicrosoftBackfillRun {

  @Id
  @Column(name = "job_id")
  private Long jobId;

  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "connection_id", nullable = false)
  private Long connectionId;

  @Enumerated(EnumType.STRING)
  @Column(name = "folder_key", nullable = false, length = 32)
  private EmailFolder folder;

  @Enumerated(EnumType.STRING)
  @Column(name = "sync_mode", nullable = false, length = 32)
  private EmailSyncMode syncMode;

  @Column(name = "from_date")
  private LocalDate fromDate;

  @Column(name = "to_date")
  private LocalDate toDate;

  @Column(name = "message_limit")
  private Integer messageLimit;

  @Column(name = "processed_count", nullable = false)
  private long processedCount;

  @Column(name = "next_url", columnDefinition = "TEXT")
  private String nextUrl;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private MicrosoftBackfillStatus status;

  @Column(name = "attempt_count", nullable = false)
  private int attemptCount;

  @Column(name = "next_attempt_at")
  private Instant nextAttemptAt;

  @Column(name = "claimed_at")
  private Instant claimedAt;

  @Column(name = "last_error", length = 1000)
  private String lastError;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void prePersist() {
    Instant now = Instant.now();
    if (status == null) status = MicrosoftBackfillStatus.PENDING;
    if (createdAt == null) createdAt = now;
    if (nextAttemptAt == null) nextAttemptAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = Instant.now();
  }
}
