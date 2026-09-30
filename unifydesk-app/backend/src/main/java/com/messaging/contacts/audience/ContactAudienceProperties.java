package com.messaging.contacts.audience;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.contacts")
public class ContactAudienceProperties {

  private int segmentMaxDepth = 4;
  private int segmentMaxPredicates = 50;
  private int segmentMaxListValues = 100;
  private int segmentMaxBytes = 32 * 1024;
  private int pageMaxSize = 200;
  private int processingBatchSize = 500;
  private int processingBatchHardMax = 2000;
  private int workerConcurrency = 4;
  private long workerPollIntervalMs = 500;
  private Duration workerStaleAfter = Duration.ofMinutes(10);
  private Duration eventFutureSkew = Duration.ofMinutes(5);
  private Duration eventMaxAge = Duration.ofDays(3650);
  private long csvMaxBytes = 100L * 1024 * 1024;
  private long csvMaxRows = 1_000_000;
  private int csvMaxColumns = 200;
  private int csvMaxCellChars = 16_384;
  private int csvPreviewRows = 20;
  private Duration artifactTtl = Duration.ofHours(24);
  private Duration signedUrlTtl = Duration.ofMinutes(10);
  private String suppressionHmacSecret = "";
  private int suppressionHmacKeyVersion = 1;

  public int boundedBatchSize() {
    return Math.max(1, Math.min(processingBatchSize, processingBatchHardMax));
  }
}
