package com.messaging.media.entity;

import com.messaging.common.id.IdGenerator;
import com.messaging.media.enums.MediaPurpose;
import com.messaging.media.enums.MediaStatus;
import com.messaging.media.enums.MediaType;
import com.messaging.media.enums.StorageProviderType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
    name = "media",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_media_storage_object",
          columnNames = {"storage_provider", "bucket", "object_key"})
    },
    indexes = {
      @Index(name = "idx_media_owner_user_id", columnList = "owner_user_id"),
      @Index(name = "idx_media_status", columnList = "status"),
      @Index(name = "idx_media_created_at", columnList = "created_at"),
      @Index(name = "idx_media_storage_provider", columnList = "storage_provider"),
      @Index(name = "idx_media_object_key", columnList = "object_key")
    })
public class Media {

  @Id private Long id;

  @Column(name = "owner_user_id", nullable = false)
  private Long ownerUserId;

  @Column(name = "company_id")
  private Long companyId;

  @Enumerated(EnumType.STRING)
  @Column(name = "storage_provider", nullable = false, length = 32)
  private StorageProviderType storageProvider;

  @Column(nullable = false, length = 120)
  private String bucket;

  @Column(name = "object_key", nullable = false, length = 1024)
  private String objectKey;

  @Column(name = "original_file_name", nullable = false, length = 255)
  private String originalFileName;

  @Column(name = "content_type", nullable = false, length = 120)
  private String contentType;

  @Column(name = "size_bytes", nullable = false)
  private long sizeBytes;

  @Enumerated(EnumType.STRING)
  @Column(name = "media_type", nullable = false, length = 32)
  private MediaType mediaType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 48)
  private MediaPurpose purpose;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private MediaStatus status;

  @Column(length = 128)
  private String checksum;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @PrePersist
  private void assignDefaults() {
    if (id == null) {
      id = IdGenerator.nextId();
    }
    if (createdAt == null) {
      createdAt = Instant.now();
    }
  }
}
