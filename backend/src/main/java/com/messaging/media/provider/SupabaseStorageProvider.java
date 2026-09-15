package com.messaging.media.provider;

import com.messaging.media.config.MediaStorageProperties;
import com.messaging.media.enums.StorageProviderType;
import com.messaging.media.exception.MediaException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

@Component
public class SupabaseStorageProvider implements StorageProvider {

  private static final Logger LOGGER = LoggerFactory.getLogger(SupabaseStorageProvider.class);

  private final MediaStorageProperties properties;
  private final RestClient restClient;

  public SupabaseStorageProvider(MediaStorageProperties properties, RestClient storageRestClient) {
    this.properties = properties;
    this.restClient = storageRestClient;
  }

  @Override
  public StorageProviderType type() {
    return StorageProviderType.SUPABASE;
  }

  @Override
  public SignedUploadResult createSignedUpload(SignedUploadRequest request) {
    Instant expiresAt = Instant.now().plus(request.expiresIn());
    Map<?, ?> body =
        postJson(
            storageUri("/object/upload/sign/%s/%s", request.bucket(), request.objectKey()),
            Map.of("expiresIn", seconds(request.expiresIn())),
            "create signed upload");
    String signedUrl = firstText(body, "signedURL", "signedUrl", "url");
    if (!StringUtils.hasText(signedUrl)) {
      LOGGER.warn("Supabase signed upload response did not include a URL");
      throw new MediaException(HttpStatus.SERVICE_UNAVAILABLE, "MEDIA_STORAGE_ERROR");
    }
    return new SignedUploadResult(
        absoluteStorageUrl(signedUrl),
        expiresAt,
        Map.of("content-type", request.contentType(), "x-upsert", "false"));
  }

  @Override
  public StorageObjectMetadata getObjectMetadata(String bucket, String objectKey) {
    try {
      return restClient
          .head()
          .uri(storageUri("/object/authenticated/%s/%s", bucket, objectKey))
          .headers(this::addAuthHeaders)
          .exchange(
              (request, response) -> {
                if (response.getStatusCode().equals(HttpStatus.NOT_FOUND)) {
                  return StorageObjectMetadata.missing();
                }
                if (response.getStatusCode().isError()) {
                  throw storageException("metadata lookup", response.getStatusCode().value());
                }
                HttpHeaders headers = response.getHeaders();
                return new StorageObjectMetadata(
                    true,
                    headers.getContentType() == null ? null : headers.getContentType().toString(),
                    headers.getContentLength() < 0 ? null : headers.getContentLength(),
                    headers.getETag());
              });
    } catch (RestClientException exception) {
      throw storageException("metadata lookup", exception);
    }
  }

  @Override
  public void move(String bucket, String sourceObjectKey, String destinationObjectKey) {
    postJson(
        storageUri("/object/move"),
        Map.of(
            "bucketId",
            bucket,
            "sourceKey",
            sourceObjectKey,
            "destinationKey",
            destinationObjectKey),
        "move");
  }

  @Override
  public SignedDownloadResult createSignedDownloadUrl(
      String bucket, String objectKey, Duration expiration) {
    Instant expiresAt = Instant.now().plus(expiration);
    Map<?, ?> body =
        postJson(
            storageUri("/object/sign/%s/%s", bucket, objectKey),
            Map.of("expiresIn", seconds(expiration)),
            "create signed download");
    String signedUrl = firstText(body, "signedURL", "signedUrl", "url");
    if (!StringUtils.hasText(signedUrl)) {
      LOGGER.warn("Supabase signed download response did not include a URL");
      throw new MediaException(HttpStatus.SERVICE_UNAVAILABLE, "MEDIA_STORAGE_ERROR");
    }
    return new SignedDownloadResult(absoluteStorageUrl(signedUrl), expiresAt);
  }

  @Override
  public void delete(String bucket, String objectKey) {
    try {
      restClient
          .delete()
          .uri(storageUri("/object/%s/%s", bucket, objectKey))
          .headers(this::addAuthHeaders)
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientException exception) {
      throw storageException("delete", exception);
    }
  }

  private Map<?, ?> postJson(URI uri, Map<String, Object> payload, String operation) {
    try {
      return restClient
          .post()
          .uri(uri)
          .headers(this::addAuthHeaders)
          .contentType(MediaType.APPLICATION_JSON)
          .body(payload)
          .retrieve()
          .body(Map.class);
    } catch (RestClientException exception) {
      throw storageException(operation, exception);
    }
  }

  private void addAuthHeaders(HttpHeaders headers) {
    String serviceRoleKey = properties.getSupabase().getServiceRoleKey();
    if (!StringUtils.hasText(serviceRoleKey)) {
      throw new MediaException(HttpStatus.SERVICE_UNAVAILABLE, "MEDIA_STORAGE_NOT_CONFIGURED");
    }
    headers.set("apikey", serviceRoleKey);
    headers.setBearerAuth(serviceRoleKey);
  }

  private URI storageUri(String template, String bucket, String objectKey) {
    String baseUrl = properties.getSupabase().getBaseUrl();
    if (!StringUtils.hasText(baseUrl)) {
      throw new MediaException(HttpStatus.SERVICE_UNAVAILABLE, "MEDIA_STORAGE_NOT_CONFIGURED");
    }
    String cleanBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    String path = template.formatted(encodeSegment(bucket), encodePathPreservingSlashes(objectKey));
    return UriComponentsBuilder.fromUriString(cleanBase + "/storage/v1" + path).build(true).toUri();
  }

  private URI storageUri(String path) {
    String baseUrl = properties.getSupabase().getBaseUrl();
    if (!StringUtils.hasText(baseUrl)) {
      throw new MediaException(HttpStatus.SERVICE_UNAVAILABLE, "MEDIA_STORAGE_NOT_CONFIGURED");
    }
    String cleanBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    return UriComponentsBuilder.fromUriString(cleanBase + "/storage/v1" + path).build(true).toUri();
  }

  private String encodePathPreservingSlashes(String value) {
    String[] segments = value.split("/");
    StringBuilder encoded = new StringBuilder();
    for (int index = 0; index < segments.length; index++) {
      if (index > 0) {
        encoded.append('/');
      }
      encoded.append(encodeSegment(segments[index]));
    }
    return encoded.toString();
  }

  private String encodeSegment(String value) {
    return UriUtils.encodePathSegment(value, java.nio.charset.StandardCharsets.UTF_8);
  }

  private String absoluteStorageUrl(String value) {
    if (value.startsWith("http://") || value.startsWith("https://")) {
      return value;
    }
    String baseUrl = properties.getSupabase().getBaseUrl();
    String cleanBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    if (value.startsWith("/storage/v1")) {
      return cleanBase + value;
    }
    return cleanBase + "/storage/v1" + (value.startsWith("/") ? value : "/" + value);
  }

  private String firstText(Map<?, ?> node, String... fields) {
    if (node == null) {
      return null;
    }
    for (String field : fields) {
      Object value = node.get(field);
      if (value instanceof String text) {
        return text;
      }
    }
    return null;
  }

  private long seconds(Duration duration) {
    return Math.max(1, duration.toSeconds());
  }

  private MediaException storageException(String operation, RestClientException exception) {
    if (exception instanceof RestClientResponseException responseException) {
      return storageException(
          operation,
          responseException.getStatusCode().value(),
          responseException.getResponseBodyAsString());
    }
    LOGGER.warn(
        "Supabase storage operation failed operation={} error={}",
        operation,
        exception.getClass().getSimpleName());
    return new MediaException(HttpStatus.SERVICE_UNAVAILABLE, "MEDIA_STORAGE_ERROR", exception);
  }

  private MediaException storageException(String operation, int status) {
    return storageException(operation, status, null);
  }

  private MediaException storageException(String operation, int status, String responseBody) {
    String trimmedBody =
        responseBody == null || responseBody.isBlank()
            ? ""
            : responseBody.substring(0, Math.min(responseBody.length(), 300));
    LOGGER.warn(
        "Supabase storage operation failed operation={} status={} response={}",
        operation,
        status,
        trimmedBody);
    if (status == HttpStatus.NOT_FOUND.value()) {
      return new MediaException(HttpStatus.SERVICE_UNAVAILABLE, "MEDIA_BUCKET_NOT_FOUND");
    }
    if (status == HttpStatus.BAD_REQUEST.value()) {
      return new MediaException(HttpStatus.SERVICE_UNAVAILABLE, "MEDIA_STORAGE_CONFIG_INVALID");
    }
    if (status == HttpStatus.UNAUTHORIZED.value() || status == HttpStatus.FORBIDDEN.value()) {
      return new MediaException(HttpStatus.SERVICE_UNAVAILABLE, "MEDIA_STORAGE_AUTH_INVALID");
    }
    return new MediaException(HttpStatus.SERVICE_UNAVAILABLE, "MEDIA_STORAGE_ERROR");
  }
}
