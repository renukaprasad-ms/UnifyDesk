package com.messaging.emailtemplate.entity;

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
@Table(
    name = "email_template_assets",
    uniqueConstraints = {
      @UniqueConstraint(name = "uk_email_template_assets_token", columnNames = "public_token"),
      @UniqueConstraint(
          name = "uk_email_template_assets_company_media",
          columnNames = {"company_id", "media_id"})
    })
public class EmailTemplateAsset {

  @Id private Long id;

  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Column(name = "media_id", nullable = false)
  private Long mediaId;

  @Column(name = "public_token", nullable = false, length = 64)
  private String publicToken;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @PrePersist
  void beforeInsert() {
    if (id == null) id = IdGenerator.nextId();
    if (createdAt == null) createdAt = Instant.now();
  }
}
