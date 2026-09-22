package com.messaging.emailtemplate.entity;

import com.messaging.emailtemplate.enums.EmailTemplateCategory;
import com.messaging.emailtemplate.enums.EmailTemplateEditorMode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "email_template_catalog")
public class EmailTemplateCatalogItem {

  @Id private Long id;

  @Column(nullable = false, unique = true, length = 100)
  private String slug;

  @Column(nullable = false, length = 160)
  private String name;

  @Column(nullable = false, length = 600)
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private EmailTemplateCategory category;

  @Enumerated(EnumType.STRING)
  @Column(name = "editor_mode", nullable = false, length = 16)
  private EmailTemplateEditorMode editorMode;

  @Column(name = "subject_template", length = 998)
  private String subjectTemplate;

  @Column(name = "preheader_template", length = 255)
  private String preheaderTemplate;

  @Column(name = "document_json", columnDefinition = "text")
  private String documentJson;

  @Column(name = "html_source", columnDefinition = "text")
  private String htmlSource;

  @Column(nullable = false)
  private boolean active;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;
}
