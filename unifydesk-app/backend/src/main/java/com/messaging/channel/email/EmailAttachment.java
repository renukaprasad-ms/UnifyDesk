package com.messaging.channel.email;

import java.io.InputStream;
import java.util.function.Supplier;

public record EmailAttachment(
    long mediaId,
    String fileName,
    String contentType,
    long sizeBytes,
    Supplier<InputStream> streamSupplier) {

  public InputStream openStream() {
    return streamSupplier.get();
  }
}
