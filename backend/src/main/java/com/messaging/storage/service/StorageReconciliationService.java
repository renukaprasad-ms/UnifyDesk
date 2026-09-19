package com.messaging.storage.service;

import com.messaging.inbox.repository.InboxMessageRepository;
import com.messaging.media.enums.MediaPurpose;
import com.messaging.media.enums.MediaStatus;
import com.messaging.media.repository.MediaRepository;
import com.messaging.storage.adapter.MediaStorageAccountingAdapter;
import com.messaging.storage.enums.StorageUsageType;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StorageReconciliationService {

  private static final Set<StorageUsageType> RECONCILED_TYPES =
      EnumSet.of(
          StorageUsageType.MESSAGE,
          StorageUsageType.MESSAGE_ATTACHMENT,
          StorageUsageType.EMAIL_ATTACHMENT,
          StorageUsageType.WHATSAPP_MEDIA,
          StorageUsageType.TEMPLATE_MEDIA,
          StorageUsageType.COMPANY_MEDIA,
          StorageUsageType.AD_CREATIVE,
          StorageUsageType.OTHER);

  private final InboxMessageRepository messageRepository;
  private final MediaRepository mediaRepository;
  private final MediaStorageAccountingAdapter mediaAccounting;
  private final StorageAccountingService accountingService;

  public void reconcileCompany(long companyId) {
    Map<StorageUsageType, MutableUsage> expected = new EnumMap<>(StorageUsageType.class);
    RECONCILED_TYPES.forEach(type -> expected.put(type, new MutableUsage()));

    expected.get(StorageUsageType.MESSAGE).usedBytes =
        messageRepository.sumStorageBytesByCompanyId(companyId);

    for (MediaRepository.MediaStorageAggregate aggregate :
        mediaRepository.aggregateStorageByCompany(companyId)) {
      MediaPurpose purpose = MediaPurpose.valueOf(aggregate.getPurpose());
      MediaStatus status = MediaStatus.valueOf(aggregate.getStatus());
      StorageUsageType usageType = mediaAccounting.usageType(purpose);
      MutableUsage usage = expected.computeIfAbsent(usageType, ignored -> new MutableUsage());
      long bytes = aggregate.getBytes() == null ? 0 : aggregate.getBytes();

      if (status == MediaStatus.ACTIVE) {
        usage.usedBytes += bytes;
      } else if (status == MediaStatus.PENDING) {
        usage.reservedBytes += bytes;
      }
    }

    for (Map.Entry<StorageUsageType, MutableUsage> entry : expected.entrySet()) {
      MutableUsage usage = entry.getValue();
      accountingService.reconcile(companyId, entry.getKey(), usage.usedBytes, usage.reservedBytes);
    }
  }

  private static final class MutableUsage {
    private long usedBytes;
    private long reservedBytes;
  }
}
