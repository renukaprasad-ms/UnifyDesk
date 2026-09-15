package com.messaging.media.service;

import com.messaging.media.config.MediaValidationProperties;
import com.messaging.media.enums.MediaType;
import com.messaging.media.exception.MediaException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;

@Service
public class MediaValidationService {

  private static final Set<Character> UNSAFE_FILE_NAME_CHARS = Set.of('/', '\\', '\0');
  private static final Map<String, String> EXTENSIONS =
      Map.of(
          "image/jpeg", "jpg",
          "image/png", "png",
          "image/webp", "webp",
          "application/pdf", "pdf");

  private final MediaValidationProperties properties;

  public MediaValidationService(MediaValidationProperties properties) {
    this.properties = properties;
  }

  public ValidatedMedia validateForUpload(String fileName, String contentType, long sizeBytes) {
    String normalizedFileName = validateFileName(fileName);
    String normalizedContentType = normalizeContentType(contentType);
    MediaType mediaType = mediaType(normalizedContentType);
    validateSize(mediaType, sizeBytes);
    String extension = extension(normalizedContentType);
    return new ValidatedMedia(
        normalizedFileName, normalizedContentType, sizeBytes, mediaType, extension);
  }

  public void validateUploadedObject(
      String expectedContentType,
      long expectedSizeBytes,
      String actualContentType,
      Long actualSizeBytes) {
    if (!isCompatibleUploadedContentType(expectedContentType, actualContentType)) {
      throw new MediaException(HttpStatus.BAD_REQUEST, "MEDIA_INVALID_TYPE");
    }
    if (actualSizeBytes == null || actualSizeBytes.longValue() != expectedSizeBytes) {
      throw new MediaException(HttpStatus.BAD_REQUEST, "MEDIA_INVALID_SIZE");
    }
    validateSize(mediaType(expectedContentType), actualSizeBytes);
  }

  private boolean isCompatibleUploadedContentType(
      String expectedContentType, String actualContentType) {
    if (!StringUtils.hasText(actualContentType)) {
      return true;
    }
    String normalized = actualContentType.trim().toLowerCase(Locale.ROOT);
    int parameters = normalized.indexOf(';');
    if (parameters >= 0) {
      normalized = normalized.substring(0, parameters).trim();
    }
    if (normalized.equals("application/octet-stream") || normalized.equals("binary/octet-stream")) {
      return true;
    }
    String normalizedActualType = normalizeContentType(normalized);
    return expectedContentType.equals(normalizedActualType);
  }

  public boolean isAllowedContentType(String contentType) {
    if (!StringUtils.hasText(contentType)) {
      return false;
    }
    return properties.getAllowedMimeTypes().contains(normalizeContentType(contentType));
  }

  public MediaType mediaType(String contentType) {
    if (contentType.startsWith("image/")) {
      return MediaType.IMAGE;
    }
    if (contentType.startsWith("video/")) {
      return MediaType.VIDEO;
    }
    if (contentType.startsWith("audio/")) {
      return MediaType.AUDIO;
    }
    if (contentType.equals("application/pdf")) {
      return MediaType.DOCUMENT;
    }
    return MediaType.OTHER;
  }

  private String validateFileName(String fileName) {
    if (!StringUtils.hasText(fileName)) {
      throw new MediaException(HttpStatus.BAD_REQUEST, "MEDIA_INVALID_FILE_NAME");
    }
    String trimmed = fileName.trim();
    if (trimmed.length() > 255 || trimmed.equals(".") || trimmed.equals("..")) {
      throw new MediaException(HttpStatus.BAD_REQUEST, "MEDIA_INVALID_FILE_NAME");
    }
    for (char character : trimmed.toCharArray()) {
      if (UNSAFE_FILE_NAME_CHARS.contains(character)) {
        throw new MediaException(HttpStatus.BAD_REQUEST, "MEDIA_INVALID_FILE_NAME");
      }
    }
    return trimmed;
  }

  private String normalizeContentType(String contentType) {
    if (!StringUtils.hasText(contentType)) {
      throw new MediaException(HttpStatus.BAD_REQUEST, "MEDIA_INVALID_TYPE");
    }
    String normalized = contentType.trim().toLowerCase(Locale.ROOT);
    int parameters = normalized.indexOf(';');
    if (parameters >= 0) {
      normalized = normalized.substring(0, parameters).trim();
    }
    if (!properties.getAllowedMimeTypes().contains(normalized)
        || !EXTENSIONS.containsKey(normalized)) {
      throw new MediaException(HttpStatus.BAD_REQUEST, "MEDIA_INVALID_TYPE");
    }
    return normalized;
  }

  private String extension(String contentType) {
    String extension = EXTENSIONS.get(contentType);
    if (extension == null) {
      throw new MediaException(HttpStatus.BAD_REQUEST, "MEDIA_INVALID_TYPE");
    }
    return extension;
  }

  private void validateSize(MediaType mediaType, long sizeBytes) {
    if (sizeBytes <= 0 || sizeBytes > maxSize(mediaType).toBytes()) {
      throw new MediaException(HttpStatus.BAD_REQUEST, "MEDIA_TOO_LARGE");
    }
  }

  private DataSize maxSize(MediaType mediaType) {
    return switch (mediaType) {
      case IMAGE -> properties.getImageMaxSize();
      case VIDEO -> properties.getVideoMaxSize();
      case AUDIO -> properties.getAudioMaxSize();
      case DOCUMENT -> properties.getDocumentMaxSize();
      case OTHER -> properties.getOtherMaxSize();
    };
  }

  public record ValidatedMedia(
      String fileName, String contentType, long sizeBytes, MediaType mediaType, String extension) {}
}
