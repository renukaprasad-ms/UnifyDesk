package com.messaging.template.entity;

import com.messaging.channel.enums.ChannelType;
import com.messaging.common.id.IdGenerator;
import com.messaging.template.enums.MessageTemplateCategory;
import com.messaging.template.enums.MessageTemplateProviderStatus;
import com.messaging.template.enums.MessageTemplateStatus;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "message_templates")
public class MessageTemplate {

  @Id private Long id;

  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Column(nullable = false, length = 160)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(name = "channel_type", nullable = false, length = 24)
  private ChannelType channelType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private MessageTemplateCategory category;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private MessageTemplateStatus status;

  @Enumerated(EnumType.STRING)
  @Column(name = "provider_status", nullable = false, length = 32)
  private MessageTemplateProviderStatus providerStatus;

  @Column(name = "provider_type", length = 40)
  private String providerType;

  @Column(name = "external_template_id", length = 255)
  private String externalTemplateId;

  @Column(length = 32)
  private String language;

  @Column(length = 998)
  private String subject;

  @Column(name = "header_text", length = 120)
  private String headerText;

  @Column(name = "body_text", nullable = false, columnDefinition = "text")
  private String bodyText;

  @Column(name = "body_html", columnDefinition = "text")
  private String bodyHtml;

  @Column(name = "footer_text", length = 120)
  private String footerText;

  @Column(name = "buttons_json", columnDefinition = "text")
  private String buttonsJson;

  @Column(name = "variables_json", columnDefinition = "text")
  private String variablesJson;

  @Column(name = "created_by_user_id", nullable = false)
  private Long createdByUserId;

  @Version
  @Column(nullable = false)
  private long version;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "archived_at")
  private Instant archivedAt;

  @PrePersist
  void beforeInsert() {
    if (id == null) id = IdGenerator.nextId();
    if (status == null) status = MessageTemplateStatus.DRAFT;
    if (providerStatus == null) providerStatus = MessageTemplateProviderStatus.LOCAL_ONLY;
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void beforeUpdate() {
    updatedAt = Instant.now();
  }
}
