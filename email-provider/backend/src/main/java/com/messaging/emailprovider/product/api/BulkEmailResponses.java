package com.messaging.emailprovider.product.api;

import com.messaging.emailprovider.mail.enums.EmailMessageStatus;
import java.util.List;

public final class BulkEmailResponses {
  private BulkEmailResponses() {}

  public record Chunk(String messageId, int recipients, EmailMessageStatus status) {}
  public record Batch(String batchId, String projectId, int totalRecipients, int suppressedRecipients,
                      int chunksCount, boolean idempotentReplay, List<Chunk> chunks) {}
}
