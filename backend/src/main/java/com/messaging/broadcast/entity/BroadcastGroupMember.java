package com.messaging.broadcast.entity;

import com.messaging.broadcast.enums.BroadcastGroupMemberStatus;
import com.messaging.common.id.IdGenerator;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "broadcast_group_members")
public class BroadcastGroupMember {
  @Id private Long id;

  @Column(name = "group_id", nullable = false)
  private Long groupId;

  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Column(nullable = false, length = 320)
  private String destination;

  @Column(name = "normalized_destination", nullable = false, length = 320)
  private String normalizedDestination;

  @Column(name = "display_name", length = 255)
  private String displayName;

  @Column(name = "metadata_json", columnDefinition = "text")
  private String metadataJson;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private BroadcastGroupMemberStatus status;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void beforeInsert() {
    if (id == null) id = IdGenerator.nextId();
    if (status == null) status = BroadcastGroupMemberStatus.ACTIVE;
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void beforeUpdate() {
    updatedAt = Instant.now();
  }
}
