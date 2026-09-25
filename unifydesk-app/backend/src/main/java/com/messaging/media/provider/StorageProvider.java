package com.messaging.media.provider;

import com.messaging.media.enums.StorageProviderType;
import java.time.Duration;

public interface StorageProvider {

  StorageProviderType type();

  SignedUploadResult createSignedUpload(SignedUploadRequest request);

  StorageObjectMetadata getObjectMetadata(String bucket, String objectKey);

  void move(String bucket, String sourceObjectKey, String destinationObjectKey);

  SignedDownloadResult createSignedDownloadUrl(
      String bucket, String objectKey, Duration expiration);

  void delete(String bucket, String objectKey);
}
