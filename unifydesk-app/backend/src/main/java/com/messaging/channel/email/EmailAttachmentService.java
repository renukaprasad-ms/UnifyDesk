package com.messaging.channel.email;

import com.messaging.common.exception.BadRequestException;
import com.messaging.media.entity.Media;
import com.messaging.media.enums.MediaPurpose;
import com.messaging.media.service.MediaService;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailAttachmentService {

  private static final int MAX_ATTACHMENTS = 10;
  private static final long MAX_TOTAL_BYTES = 18L * 1024L * 1024L;

  private final MediaService mediaService;

  public List<EmailAttachment> resolve(long companyId, List<Long> mediaIds) {
    if (mediaIds == null || mediaIds.isEmpty()) return List.of();

    LinkedHashSet<Long> unique = new LinkedHashSet<>(mediaIds);
    if (unique.size() > MAX_ATTACHMENTS) {
      throw new BadRequestException("Email cannot contain more than " + MAX_ATTACHMENTS + " attachments");
    }

    List<EmailAttachment> attachments = new ArrayList<>(unique.size());
    long total = 0;
    for (Long mediaId : unique) {
      if (mediaId == null) throw new BadRequestException("Invalid email attachment");
      Media media = mediaService.requireActiveCompanyMedia(mediaId, companyId);
      if (media.getPurpose() != MediaPurpose.EMAIL_ATTACHMENT) {
        throw new BadRequestException("Media is not an email attachment");
      }

      long size = Math.max(0L, media.getSizeBytes());
      total = Math.addExact(total, size);
      if (total > MAX_TOTAL_BYTES) {
        throw new BadRequestException("Email attachments exceed the 18 MB safe send limit");
      }

      String fileName =
          media.getOriginalFileName() == null || media.getOriginalFileName().isBlank()
              ? "attachment"
              : media.getOriginalFileName();
      String contentType =
          media.getContentType() == null || media.getContentType().isBlank()
              ? "application/octet-stream"
              : media.getContentType();

      attachments.add(
          new EmailAttachment(
              media.getId(),
              fileName,
              contentType,
              size,
              () -> mediaService.openTrustedObject(media.getId(), companyId)));
    }
    return List.copyOf(attachments);
  }
}
