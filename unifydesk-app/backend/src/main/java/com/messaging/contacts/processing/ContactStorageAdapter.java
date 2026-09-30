package com.messaging.contacts.processing;

import com.messaging.contacts.audience.ContactAudienceProperties;
import com.messaging.media.config.MediaStorageProperties;
import com.messaging.media.enums.StorageProviderType;
import com.messaging.media.provider.*;
import com.messaging.storage.enums.StorageUsageType;
import com.messaging.storage.service.StorageAccountingService;
import com.messaging.storage.service.StorageQuotaService;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ContactStorageAdapter {

  private final StorageProviderResolver providerResolver;
  private final MediaStorageProperties storageProperties;
  private final StorageQuotaService quotaService;
  private final StorageAccountingService accountingService;
  private final ContactAudienceProperties contactsProperties;

  public UploadIntent createImportUpload(
      long companyId, long importId, long expectedBytes, String objectKey) {
    if (expectedBytes <= 0 || expectedBytes > contactsProperties.getCsvMaxBytes()) {
      throw new com.messaging.common.exception.BadRequestException("CONTACT_IMPORT_INVALID");
    }
    StorageProviderType type = storageProperties.getDefaultProvider();
    String bucket = bucket(type);
    quotaService.reserve(
        companyId,
        StorageUsageType.CONTACT_DATA,
        "CONTACT_IMPORT",
        Long.toString(importId),
        expectedBytes);
    SignedUploadResult signed =
        providerResolver
            .resolve(type)
            .createSignedUpload(
                new SignedUploadRequest(
                    bucket,
                    objectKey,
                    "text/csv",
                    expectedBytes,
                    storageProperties.getUploadUrlExpiry()));
    return new UploadIntent(
        type,
        bucket,
        objectKey,
        expectedBytes,
        signed.uploadUrl(),
        signed.expiresAt(),
        signed.requiredHeaders());
  }

  public StorageObjectMetadata metadata(StorageRef ref) {
    return providerResolver.resolve(ref.provider()).getObjectMetadata(ref.bucket(), ref.objectKey());
  }

  public InputStream openStream(StorageRef ref) {
    SignedDownloadResult signed =
        providerResolver
            .resolve(ref.provider())
            .createSignedDownloadUrl(ref.bucket(), ref.objectKey(), contactsProperties.getSignedUrlTtl());
    try {
      HttpClient client =
          HttpClient.newBuilder()
              .connectTimeout(storageProperties.getHttpConnectTimeout())
              .followRedirects(HttpClient.Redirect.NORMAL)
              .build();
      HttpResponse<InputStream> response =
          client.send(
              HttpRequest.newBuilder(URI.create(signed.url()))
                  .timeout(storageProperties.getHttpReadTimeout())
                  .GET()
                  .build(),
              HttpResponse.BodyHandlers.ofInputStream());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        response.body().close();
        throw new IllegalStateException("CONTACT_STORAGE_READ_FAILED");
      }
      return response.body();
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("CONTACT_STORAGE_READ_INTERRUPTED", exception);
    }
  }

  public PublishedObject publishFile(
      long companyId,
      StorageUsageType usageType,
      String resourceType,
      String resourceId,
      Path file,
      long sizeBytes,
      String objectKey) {
    StorageProviderType type = storageProperties.getDefaultProvider();
    String bucket = bucket(type);
    quotaService.reserve(companyId, usageType, resourceType, resourceId, sizeBytes);
    SignedUploadResult signed =
        providerResolver
            .resolve(type)
            .createSignedUpload(
                new SignedUploadRequest(
                    bucket,
                    objectKey,
                    "text/csv",
                    sizeBytes,
                    storageProperties.getUploadUrlExpiry()));
    try {
      HttpRequest.Builder request =
          HttpRequest.newBuilder(URI.create(signed.uploadUrl()))
              .timeout(Duration.ofMinutes(5))
              .PUT(HttpRequest.BodyPublishers.ofFile(file));
      signed.requiredHeaders().forEach(request::header);
      HttpClient client =
          HttpClient.newBuilder()
              .connectTimeout(storageProperties.getHttpConnectTimeout())
              .followRedirects(HttpClient.Redirect.NORMAL)
              .build();
      HttpResponse<Void> response = client.send(request.build(), HttpResponse.BodyHandlers.discarding());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        accountingService.releaseReservation(companyId, usageType, resourceType, resourceId, sizeBytes);
        throw new IllegalStateException("CONTACT_STORAGE_WRITE_FAILED");
      }
      accountingService.commitReservation(companyId, usageType, resourceType, resourceId, sizeBytes);
      return new PublishedObject(type, bucket, objectKey, sizeBytes);
    } catch (IOException exception) {
      accountingService.releaseReservation(companyId, usageType, resourceType, resourceId, sizeBytes);
      throw new UncheckedIOException(exception);
    } catch (InterruptedException exception) {
      accountingService.releaseReservation(companyId, usageType, resourceType, resourceId, sizeBytes);
      Thread.currentThread().interrupt();
      throw new IllegalStateException("CONTACT_STORAGE_WRITE_INTERRUPTED", exception);
    }
  }

  public SignedDownloadResult signedDownload(StorageRef ref) {
    return providerResolver
        .resolve(ref.provider())
        .createSignedDownloadUrl(ref.bucket(), ref.objectKey(), contactsProperties.getSignedUrlTtl());
  }

  public void commitImportReservation(long companyId, long importId, long bytes) {
    accountingService.commitReservation(
        companyId,
        StorageUsageType.CONTACT_DATA,
        "CONTACT_IMPORT",
        Long.toString(importId),
        bytes);
  }

  public void releaseImportReservation(long companyId, long importId, long bytes) {
    accountingService.releaseReservation(
        companyId,
        StorageUsageType.CONTACT_DATA,
        "CONTACT_IMPORT",
        Long.toString(importId),
        bytes);
  }

  private String bucket(StorageProviderType type) {
    String value =
        type == StorageProviderType.S3
            ? storageProperties.getS3().getBucket()
            : storageProperties.getSupabase().getBucket();
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("CONTACT_STORAGE_NOT_CONFIGURED");
    }
    return value;
  }

  public record StorageRef(StorageProviderType provider, String bucket, String objectKey) {}

  public record UploadIntent(
      StorageProviderType provider,
      String bucket,
      String objectKey,
      long expectedBytes,
      String uploadUrl,
      java.time.Instant expiresAt,
      Map<String, String> requiredHeaders) {}

  public record PublishedObject(
      StorageProviderType provider, String bucket, String objectKey, long sizeBytes) {}
}
