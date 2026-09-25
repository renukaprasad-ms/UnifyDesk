package com.messaging.storage.adapter;

import com.messaging.media.entity.Media;
import com.messaging.media.enums.MediaPurpose;
import com.messaging.media.enums.MediaStatus;
import com.messaging.storage.enums.StorageUsageType;
import com.messaging.storage.service.StorageAccountingService;
import com.messaging.storage.service.StorageQuotaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MediaStorageAccountingAdapter {

  private static final String RESOURCE_TYPE = "MEDIA";

  private final StorageQuotaService quotaService;
  private final StorageAccountingService accountingService;

  public void reserve(Media media) {
    if (!isCompanyScoped(media) || media.getStatus() != MediaStatus.PENDING) {
      return;
    }
    quotaService.reserve(
        media.getCompanyId(),
        usageType(media.getPurpose()),
        RESOURCE_TYPE,
        media.getId().toString(),
        media.getSizeBytes());
  }

  public void commit(Media media) {
    if (!isCompanyScoped(media)) {
      return;
    }
    accountingService.commitReservation(
        media.getCompanyId(),
        usageType(media.getPurpose()),
        RESOURCE_TYPE,
        media.getId().toString(),
        media.getSizeBytes());
  }

  public void release(Media media) {
    if (!isCompanyScoped(media)) {
      return;
    }

    if (media.getStatus() == MediaStatus.PENDING) {
      accountingService.releaseReservation(
          media.getCompanyId(),
          usageType(media.getPurpose()),
          RESOURCE_TYPE,
          media.getId().toString(),
          media.getSizeBytes());
      return;
    }

    if (media.getStatus() == MediaStatus.ACTIVE) {
      accountingService.releaseUsage(
          media.getCompanyId(),
          usageType(media.getPurpose()),
          RESOURCE_TYPE,
          media.getId().toString(),
          media.getSizeBytes());
    }
  }

  public void attachActiveMediaToCompany(Media media, long companyId) {
    if (media.getStatus() != MediaStatus.ACTIVE) {
      throw new IllegalArgumentException("Only active media can be assigned to a company");
    }

    quotaService.consume(
        companyId,
        usageType(media.getPurpose()),
        RESOURCE_TYPE,
        media.getId().toString(),
        media.getSizeBytes());
  }

  public StorageUsageType usageType(MediaPurpose purpose) {
    return switch (purpose) {
      case MESSAGE_ATTACHMENT -> StorageUsageType.MESSAGE_ATTACHMENT;
      case EMAIL_ATTACHMENT -> StorageUsageType.EMAIL_ATTACHMENT;
      case WHATSAPP_MEDIA -> StorageUsageType.WHATSAPP_MEDIA;
      case TEMPLATE_MEDIA -> StorageUsageType.TEMPLATE_MEDIA;
      case AD_CREATIVE -> StorageUsageType.AD_CREATIVE;
      case COMPANY_LOGO -> StorageUsageType.COMPANY_MEDIA;
      case USER_PROFILE, USER_COVER, OTHER -> StorageUsageType.OTHER;
    };
  }

  private boolean isCompanyScoped(Media media) {
    return media.getCompanyId() != null;
  }
}
