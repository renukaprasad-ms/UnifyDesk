package com.messaging.channel.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.messaging.common.exception.BadRequestException;
import com.messaging.media.entity.Media;
import com.messaging.media.enums.MediaPurpose;
import com.messaging.media.service.MediaService;
import java.io.ByteArrayInputStream;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EmailAttachmentServiceTests {

  private static final long COMPANY_ID = 10L;

  private MediaService mediaService;
  private EmailAttachmentService service;

  @BeforeEach
  void setUp() {
    mediaService = mock(MediaService.class);
    service = new EmailAttachmentService(mediaService);
  }

  @Test
  void resolvesActiveCompanyEmailAttachmentAsStreamingDescriptor() throws Exception {
    Media media = media(100L, "invoice.pdf", "application/pdf", 4L);
    when(mediaService.requireActiveCompanyMedia(100L, COMPANY_ID)).thenReturn(media);
    when(mediaService.openTrustedObject(100L, COMPANY_ID))
        .thenReturn(new ByteArrayInputStream(new byte[] {1, 2, 3, 4}));

    List<EmailAttachment> result = service.resolve(COMPANY_ID, List.of(100L));

    assertThat(result).hasSize(1);
    EmailAttachment attachment = result.getFirst();
    assertThat(attachment.mediaId()).isEqualTo(100L);
    assertThat(attachment.fileName()).isEqualTo("invoice.pdf");
    assertThat(attachment.contentType()).isEqualTo("application/pdf");
    assertThat(attachment.sizeBytes()).isEqualTo(4L);
    try (var input = attachment.openStream()) {
      assertThat(input.readAllBytes()).containsExactly(1, 2, 3, 4);
    }
  }

  @Test
  void rejectsMediaWithNonEmailPurpose() {
    Media media = media(100L, "creative.png", "image/png", 4L);
    media.setPurpose(MediaPurpose.AD_CREATIVE);
    when(mediaService.requireActiveCompanyMedia(100L, COMPANY_ID)).thenReturn(media);

    assertThatThrownBy(() -> service.resolve(COMPANY_ID, List.of(100L)))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("not an email attachment");
  }

  @Test
  void rejectsMoreThanTenAttachmentsBeforeStorageAccess() {
    List<Long> ids =
        List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L);

    assertThatThrownBy(() -> service.resolve(COMPANY_ID, ids))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("10 attachments");
  }

  @Test
  void rejectsTotalAttachmentSizeAboveSafeSendLimit() {
    Media first = media(100L, "one.bin", "application/octet-stream", 10L * 1024L * 1024L);
    Media second = media(101L, "two.bin", "application/octet-stream", 9L * 1024L * 1024L);
    when(mediaService.requireActiveCompanyMedia(100L, COMPANY_ID)).thenReturn(first);
    when(mediaService.requireActiveCompanyMedia(101L, COMPANY_ID)).thenReturn(second);

    assertThatThrownBy(() -> service.resolve(COMPANY_ID, List.of(100L, 101L)))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("18 MB");
  }

  private Media media(long id, String fileName, String contentType, long sizeBytes) {
    Media media = new Media();
    media.setId(id);
    media.setCompanyId(COMPANY_ID);
    media.setOriginalFileName(fileName);
    media.setContentType(contentType);
    media.setSizeBytes(sizeBytes);
    media.setPurpose(MediaPurpose.EMAIL_ATTACHMENT);
    return media;
  }
}
