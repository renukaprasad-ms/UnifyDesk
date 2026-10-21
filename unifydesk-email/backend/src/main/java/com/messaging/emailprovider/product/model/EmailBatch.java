package com.messaging.emailprovider.product.model;

import com.messaging.emailprovider.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "email_product_batches")
public class EmailBatch extends BaseEntity {
  @Column(name = "project_id", nullable = false)
  private Long projectId;

  @Column(name = "idempotency_key", nullable = false, length = 128)
  private String idempotencyKey;

  @Column(name = "request_hash", nullable = false, length = 64)
  private String requestHash;

  @Column(name = "total_recipients", nullable = false)
  private int totalRecipients;

  @Column(name = "chunks_count", nullable = false)
  private int chunksCount;

  @Column(name = "suppressed_recipients", nullable = false)
  private int suppressedRecipients;
}
