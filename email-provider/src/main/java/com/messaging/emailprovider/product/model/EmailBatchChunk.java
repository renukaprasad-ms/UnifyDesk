package com.messaging.emailprovider.product.model;

import com.messaging.emailprovider.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor @Entity
@Table(name = "email_product_batch_chunks")
public class EmailBatchChunk extends BaseEntity {
  @Column(name = "batch_id", nullable = false)
  private Long batchId;
  @Column(name = "chunk_number", nullable = false)
  private int chunkNumber;
  @Column(name = "message_id", nullable = false)
  private Long messageId;
  @Column(name = "recipient_count", nullable = false)
  private int recipientCount;
}
