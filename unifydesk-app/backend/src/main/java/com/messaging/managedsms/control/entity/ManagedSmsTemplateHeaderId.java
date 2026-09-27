package com.messaging.managedsms.control.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
@Embeddable
public class ManagedSmsTemplateHeaderId implements Serializable {
  @Column(name = "template_version_id") private Long templateVersionId;
  @Column(name = "header_id") private Long headerId;
}
