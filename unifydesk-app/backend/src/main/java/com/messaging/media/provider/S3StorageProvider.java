package com.messaging.media.provider;

import com.messaging.media.config.MediaStorageProperties;
import com.messaging.media.enums.StorageProviderType;
import com.messaging.media.exception.MediaException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Component
public class S3StorageProvider implements StorageProvider {

  private final MediaStorageProperties properties;
  private volatile S3Client client;
  private volatile S3Presigner presigner;

  public S3StorageProvider(MediaStorageProperties properties) {
    this.properties = properties;
  }

  @Override
  public StorageProviderType type() {
    return StorageProviderType.S3;
  }

  @Override
  public SignedUploadResult createSignedUpload(SignedUploadRequest request) {
    ensureConfigured();
    Instant expiresAt = Instant.now().plus(request.expiresIn());

    PutObjectRequest putObject =
        PutObjectRequest.builder()
            .bucket(request.bucket())
            .key(request.objectKey())
            .contentType(request.contentType())
            .build();

    var signed =
        presigner()
            .presignPutObject(
                PutObjectPresignRequest.builder()
                    .signatureDuration(request.expiresIn())
                    .putObjectRequest(putObject)
                    .build());

    return new SignedUploadResult(
        signed.url().toString(),
        expiresAt,
        Map.of("content-type", request.contentType()));
  }

  @Override
  public StorageObjectMetadata getObjectMetadata(String bucket, String objectKey) {
    ensureConfigured();
    try {
      var response =
          client()
              .headObject(
                  HeadObjectRequest.builder().bucket(bucket).key(objectKey).build());
      return new StorageObjectMetadata(
          true,
          response.contentType(),
          response.contentLength(),
          response.eTag());
    } catch (NoSuchKeyException exception) {
      return StorageObjectMetadata.missing();
    } catch (S3Exception exception) {
      if (exception.statusCode() == HttpStatus.NOT_FOUND.value()) {
        return StorageObjectMetadata.missing();
      }
      throw storageException(exception);
    }
  }

  @Override
  public InputStream openObject(String bucket, String objectKey) {
    ensureConfigured();
    try {
      return client().getObject(
          GetObjectRequest.builder().bucket(bucket).key(objectKey).build());
    } catch (S3Exception exception) {
      throw storageException(exception);
    }
  }

  @Override
  public byte[] readObject(String bucket, String objectKey) {
    ensureConfigured();
    try {
      return client()
          .getObjectAsBytes(
              GetObjectRequest.builder().bucket(bucket).key(objectKey).build())
          .asByteArray();
    } catch (S3Exception exception) {
      throw storageException(exception);
    }
  }

  @Override
  public void writeObject(String bucket, String objectKey, byte[] content, String contentType) {
    ensureConfigured();
    try {
      client()
          .putObject(
              PutObjectRequest.builder()
                  .bucket(bucket)
                  .key(objectKey)
                  .contentType(contentType)
                  .build(),
              RequestBody.fromBytes(content));
    } catch (S3Exception exception) {
      throw storageException(exception);
    }
  }

  @Override
  public void move(String bucket, String sourceObjectKey, String destinationObjectKey) {
    ensureConfigured();
    try {
      client()
          .copyObject(
              CopyObjectRequest.builder()
                  .bucket(bucket)
                  .key(destinationObjectKey)
                  .copySource(encodeCopySource(bucket, sourceObjectKey))
                  .build());
      delete(bucket, sourceObjectKey);
    } catch (S3Exception exception) {
      throw storageException(exception);
    }
  }

  @Override
  public SignedDownloadResult createSignedDownloadUrl(
      String bucket, String objectKey, Duration expiration) {
    ensureConfigured();
    Instant expiresAt = Instant.now().plus(expiration);
    GetObjectRequest request =
        GetObjectRequest.builder().bucket(bucket).key(objectKey).build();
    var signed =
        presigner()
            .presignGetObject(
                GetObjectPresignRequest.builder()
                    .signatureDuration(expiration)
                    .getObjectRequest(request)
                    .build());
    return new SignedDownloadResult(signed.url().toString(), expiresAt);
  }

  @Override
  public void delete(String bucket, String objectKey) {
    ensureConfigured();
    try {
      client()
          .deleteObject(
              DeleteObjectRequest.builder().bucket(bucket).key(objectKey).build());
    } catch (S3Exception exception) {
      throw storageException(exception);
    }
  }

  private S3Client client() {
    S3Client current = client;
    if (current != null) return current;
    synchronized (this) {
      if (client == null) {
        var builder =
            S3Client.builder()
                .credentialsProvider(credentialsProvider())
                .region(region())
                .serviceConfiguration(
                    S3Configuration.builder()
                        .pathStyleAccessEnabled(properties.getS3().isPathStyleAccessEnabled())
                        .build());
        URI endpoint = endpoint();
        if (endpoint != null) builder.endpointOverride(endpoint);
        client = builder.build();
      }
      return client;
    }
  }

  private S3Presigner presigner() {
    S3Presigner current = presigner;
    if (current != null) return current;
    synchronized (this) {
      if (presigner == null) {
        var builder =
            S3Presigner.builder()
                .credentialsProvider(credentialsProvider())
                .region(region())
                .serviceConfiguration(
                    S3Configuration.builder()
                        .pathStyleAccessEnabled(properties.getS3().isPathStyleAccessEnabled())
                        .build());
        URI endpoint = endpoint();
        if (endpoint != null) builder.endpointOverride(endpoint);
        presigner = builder.build();
      }
      return presigner;
    }
  }

  private AwsCredentialsProvider credentialsProvider() {
    String accessKey = properties.getS3().getAccessKeyId();
    String secretKey = properties.getS3().getSecretAccessKey();
    if (StringUtils.hasText(accessKey) && StringUtils.hasText(secretKey)) {
      return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
    }
    return DefaultCredentialsProvider.create();
  }

  private Region region() {
    String region = properties.getS3().getRegion();
    if (!StringUtils.hasText(region)) {
      throw new MediaException(HttpStatus.SERVICE_UNAVAILABLE, "MEDIA_STORAGE_NOT_CONFIGURED");
    }
    return Region.of(region.trim());
  }

  private URI endpoint() {
    String endpoint = properties.getS3().getEndpoint();
    return StringUtils.hasText(endpoint) ? URI.create(endpoint.trim()) : null;
  }

  private void ensureConfigured() {
    if (!StringUtils.hasText(properties.getS3().getBucket())
        || !StringUtils.hasText(properties.getS3().getRegion())) {
      throw new MediaException(HttpStatus.SERVICE_UNAVAILABLE, "MEDIA_STORAGE_NOT_CONFIGURED");
    }
  }

  private String encodeCopySource(String bucket, String objectKey) {
    return URLEncoder.encode(bucket + "/" + objectKey, StandardCharsets.UTF_8)
        .replace("+", "%20")
        .replace("%2F", "/");
  }

  private MediaException storageException(S3Exception exception) {
    if (exception.statusCode() == HttpStatus.FORBIDDEN.value()
        || exception.statusCode() == HttpStatus.UNAUTHORIZED.value()) {
      return new MediaException(HttpStatus.SERVICE_UNAVAILABLE, "MEDIA_STORAGE_AUTH_INVALID", exception);
    }
    if (exception.statusCode() == HttpStatus.NOT_FOUND.value()) {
      return new MediaException(HttpStatus.SERVICE_UNAVAILABLE, "MEDIA_BUCKET_NOT_FOUND", exception);
    }
    return new MediaException(HttpStatus.SERVICE_UNAVAILABLE, "MEDIA_STORAGE_ERROR", exception);
  }
}
