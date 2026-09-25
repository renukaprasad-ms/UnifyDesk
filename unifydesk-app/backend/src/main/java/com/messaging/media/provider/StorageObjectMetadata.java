package com.messaging.media.provider;

public record StorageObjectMetadata(
    boolean exists, String contentType, Long sizeBytes, String checksum) {

  public static StorageObjectMetadata missing() {
    return new StorageObjectMetadata(false, null, null, null);
  }
}
