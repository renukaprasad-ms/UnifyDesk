package com.messaging.channel.email;

import jakarta.activation.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class EmailAttachmentDataSource implements DataSource {

  private final EmailAttachment attachment;

  public EmailAttachmentDataSource(EmailAttachment attachment) {
    this.attachment = attachment;
  }

  @Override
  public InputStream getInputStream() throws IOException {
    try {
      return attachment.openStream();
    } catch (RuntimeException exception) {
      throw new IOException("Unable to open email attachment", exception);
    }
  }

  @Override
  public OutputStream getOutputStream() throws IOException {
    throw new IOException("Email attachment data source is read-only");
  }

  @Override
  public String getContentType() {
    return attachment.contentType();
  }

  @Override
  public String getName() {
    return attachment.fileName();
  }
}
