package com.messaging.media.service;

import com.messaging.media.enums.MediaPurpose;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class MediaObjectKeyFactory {

  public String createUserObjectKey(
      long ownerUserId, MediaPurpose purpose, long mediaId, String extension) {
    return "users/%d/%s/%d.%s"
        .formatted(ownerUserId, purposePath(purpose), mediaId, extension.toLowerCase(Locale.ROOT));
  }

  public String createTemporaryObjectKey(long ownerUserId, long mediaId, String extension) {
    return "tmp/uploads/%d/%d.%s".formatted(ownerUserId, mediaId, extension.toLowerCase(Locale.ROOT));
  }

  public String createPreRegisterObjectKey(long mediaId, String extension) {
    return "tmp/register/%d.%s".formatted(mediaId, extension.toLowerCase(Locale.ROOT));
  }

  private String purposePath(MediaPurpose purpose) {
    return switch (purpose) {
      case USER_PROFILE -> "profile";
      case USER_COVER -> "cover";
      case MESSAGE_ATTACHMENT -> "message-attachment";
      case EMAIL_ATTACHMENT -> "email-attachment";
      case WHATSAPP_MEDIA -> "whatsapp";
      case COMPANY_LOGO -> "company-logo";
      case TEMPLATE_MEDIA -> "template";
      case AD_CREATIVE -> "ad-creative";
      case OTHER -> "other";
    };
  }
}
