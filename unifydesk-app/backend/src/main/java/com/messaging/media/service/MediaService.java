package com.messaging.media.service;

import com.messaging.common.id.IdGenerator;
import com.messaging.company.iam.CompanyPermissionKeys;
import com.messaging.company.service.CompanyAuthorizationService;
import com.messaging.media.config.MediaStorageProperties;
import com.messaging.media.config.MediaValidationProperties;
import com.messaging.media.dto.InitiateMediaUploadRequest;
import com.messaging.media.dto.InitiateMediaUploadResponse;
import com.messaging.media.dto.MediaAccessResponse;
import com.messaging.media.dto.MediaResponse;
import com.messaging.media.entity.Media;
import com.messaging.media.enums.MediaPurpose;
import com.messaging.media.enums.MediaStatus;
import com.messaging.media.exception.MediaException;
import com.messaging.media.mapper.MediaMapper;
import com.messaging.media.provider.SignedDownloadResult;
import com.messaging.media.provider.SignedUploadRequest;
import com.messaging.media.provider.SignedUploadResult;
import com.messaging.media.provider.StorageObjectMetadata;
import com.messaging.media.provider.StorageProvider;
import com.messaging.media.provider.StorageProviderResolver;
import com.messaging.media.repository.MediaRepository;
import com.messaging.redis.key.RedisKey;
import com.messaging.redis.service.RedisService;
import com.messaging.security.service.CurrentUserService;
import com.messaging.storage.adapter.MediaStorageAccountingAdapter;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MediaService {

  private static final long PRE_REGISTER_OWNER_ID = 0L;

  private final MediaRepository mediaRepository;
  private final CurrentUserService currentUserService;
  private final MediaValidationService validationService;
  private final MediaObjectKeyFactory objectKeyFactory;
  private final StorageProviderResolver storageProviderResolver;
  private final MediaStorageProperties storageProperties;
  private final MediaValidationProperties validationProperties;
  private final MediaAuthorizationService authorizationService;
  private final MediaMapper mediaMapper;
  private final RedisService redisService;
  private final MediaStorageAccountingAdapter storageAccounting;
  private final CompanyAuthorizationService companyAuthorizationService;
  private final SecureRandom secureRandom = new SecureRandom();

  @Transactional
  public InitiateMediaUploadResponse initiateUpload(InitiateMediaUploadRequest request) {
    MediaValidationService.ValidatedMedia validated =
        validationService.validateForUpload(
            request.fileName(), request.contentType(), request.sizeBytes());
    Long userId = currentUserService.currentUserIdOrEmpty().orElse(null);
    boolean preRegisterUpload = userId == null;
    if (preRegisterUpload && request.purpose() != MediaPurpose.USER_PROFILE) {
      throw new MediaException(HttpStatus.BAD_REQUEST, "MEDIA_INVALID_PURPOSE");
    }

    if (!preRegisterUpload) {
      validateCompanyScope(userId, request, validated.sizeBytes());
    }

    Media media =
        preRegisterUpload
            ? createPreRegisterPendingMedia(request, validated)
            : createPendingMedia(userId, request, validated);
    storageAccounting.reserve(media);

    StorageProvider provider = storageProviderResolver.resolve(media.getStorageProvider());
    SignedUploadResult signedUpload =
        provider.createSignedUpload(
            new SignedUploadRequest(
                media.getBucket(),
                media.getObjectKey(),
                media.getContentType(),
                media.getSizeBytes(),
                storageProperties.getUploadUrlExpiry()));
    String uploadToken = preRegisterUpload ? secureToken() : null;
    if (uploadToken != null) {
      redisService.set(
          RedisKey.preRegisterMedia(media.getId().toString()),
          uploadToken,
          validationProperties.getPendingTtl());
    }

    return new InitiateMediaUploadResponse(
        media.getId().toString(),
        uploadToken,
        signedUpload.uploadUrl(),
        signedUpload.expiresAt(),
        signedUpload.requiredHeaders());
  }

  @Transactional
  protected Media createPendingMedia(
      long userId,
      InitiateMediaUploadRequest request,
      MediaValidationService.ValidatedMedia validated) {
    long mediaId = IdGenerator.nextId();
    Media media = new Media();
    media.setId(mediaId);
    media.setOwnerUserId(userId);
    media.setCompanyId(request.companyId());
    media.setStorageProvider(storageProperties.getDefaultProvider());
    media.setBucket(configuredBucket());
    media.setObjectKey(
        objectKeyFactory.createTemporaryObjectKey(userId, mediaId, validated.extension()));
    media.setOriginalFileName(validated.fileName());
    media.setContentType(validated.contentType());
    media.setSizeBytes(validated.sizeBytes());
    media.setMediaType(validated.mediaType());
    media.setPurpose(request.purpose());
    media.setStatus(MediaStatus.PENDING);
    return mediaRepository.saveAndFlush(media);
  }

  @Transactional
  protected Media createPreRegisterPendingMedia(
      InitiateMediaUploadRequest request, MediaValidationService.ValidatedMedia validated) {
    long mediaId = IdGenerator.nextId();
    Media media = new Media();
    media.setId(mediaId);
    media.setOwnerUserId(PRE_REGISTER_OWNER_ID);
    media.setStorageProvider(storageProperties.getDefaultProvider());
    media.setBucket(configuredBucket());
    media.setObjectKey(objectKeyFactory.createPreRegisterObjectKey(mediaId, validated.extension()));
    media.setOriginalFileName(validated.fileName());
    media.setContentType(validated.contentType());
    media.setSizeBytes(validated.sizeBytes());
    media.setMediaType(validated.mediaType());
    media.setPurpose(request.purpose());
    media.setStatus(MediaStatus.PENDING);
    return mediaRepository.saveAndFlush(media);
  }

  @Transactional
  public MediaResponse completeUpload(String mediaId) {
    long userId = currentUserService.currentUserId();
    Media media = loadOwnedMedia(parseMediaId(mediaId), userId);
    if (media.getStatus() == MediaStatus.ACTIVE) {
      return mediaMapper.toResponse(media);
    }
    if (media.getStatus() != MediaStatus.PENDING) {
      throw new MediaException(HttpStatus.CONFLICT, "MEDIA_INVALID_STATE");
    }

    return mediaMapper.toResponse(completePendingMedia(media, userId));
  }

  @Transactional
  public Media completePreRegisterProfilePictureUpload(
      String mediaId, String uploadToken, long userId) {
    long parsedMediaId = parseMediaId(mediaId);
    String redisKey = RedisKey.preRegisterMedia(mediaId);
    String storedToken = redisService.get(redisKey, String.class);
    if (!tokenMatches(storedToken, uploadToken)) {
      throw new MediaException(HttpStatus.BAD_REQUEST, "MEDIA_UPLOAD_TOKEN_INVALID");
    }
    Media media =
        mediaRepository
            .findById(parsedMediaId)
            .orElseThrow(() -> new MediaException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND"));
    if (media.getStatus() != MediaStatus.PENDING) {
      throw new MediaException(HttpStatus.CONFLICT, "MEDIA_INVALID_STATE");
    }
    if (media.getOwnerUserId() != PRE_REGISTER_OWNER_ID
        || media.getPurpose() != MediaPurpose.USER_PROFILE) {
      throw new MediaException(HttpStatus.BAD_REQUEST, "MEDIA_UPLOAD_TOKEN_INVALID");
    }

    Media completed = completePendingMedia(media, userId);
    redisService.delete(redisKey);
    return completed;
  }

  private Media completePendingMedia(Media media, long ownerUserId) {
    StorageProvider provider = storageProviderResolver.resolve(media.getStorageProvider());
    StorageObjectMetadata metadata =
        provider.getObjectMetadata(media.getBucket(), media.getObjectKey());
    try {
      if (!metadata.exists()) {
        markFailed(media.getId());
        throw new MediaException(HttpStatus.BAD_REQUEST, "MEDIA_STORAGE_OBJECT_MISSING");
      }
      validationService.validateUploadedObject(
          media.getContentType(),
          media.getSizeBytes(),
          metadata.contentType(),
          metadata.sizeBytes());
    } catch (MediaException exception) {
      markFailed(media.getId());
      throw exception;
    }

    String finalObjectKey = finalObjectKey(media, ownerUserId);
    if (!media.getObjectKey().equals(finalObjectKey)) {
      provider.move(media.getBucket(), media.getObjectKey(), finalObjectKey);
    }
    Media active = markActive(media.getId(), ownerUserId, finalObjectKey, metadata.checksum());
    storageAccounting.commit(active);
    return active;
  }

  public MediaAccessResponse createAccessUrl(String mediaId) {
    long userId = currentUserService.currentUserId();
    Media media =
        mediaRepository
            .findById(parseMediaId(mediaId))
            .orElseThrow(() -> new MediaException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND"));
    authorizationService.requireReadAccess(media, userId);
    if (media.getStatus() != MediaStatus.ACTIVE) {
      throw new MediaException(HttpStatus.CONFLICT, "MEDIA_NOT_ACTIVE");
    }
    StorageProvider provider = storageProviderResolver.resolve(media.getStorageProvider());
    SignedDownloadResult signedDownload =
        provider.createSignedDownloadUrl(
            media.getBucket(), media.getObjectKey(), storageProperties.getDownloadUrlExpiry());
    return new MediaAccessResponse(signedDownload.url(), signedDownload.expiresAt());
  }

  @Transactional
  public void assignCompanyMedia(long mediaId, long companyId, long ownerUserId) {
    Media media = getActiveOwnedMedia(mediaId, ownerUserId);
    if (media.getCompanyId() != null && media.getCompanyId().equals(companyId)) {
      return;
    }
    if (media.getCompanyId() != null) {
      throw new MediaException(HttpStatus.CONFLICT, "MEDIA_ALREADY_ASSIGNED");
    }

    storageAccounting.attachActiveMediaToCompany(media, companyId);
    media.setCompanyId(companyId);
    mediaRepository.saveAndFlush(media);
  }

  @Transactional
  public void retirePersonalProfilePicture(long mediaId, long ownerUserId) {
    Media media = mediaRepository.findById(mediaId).orElse(null);
    if (media == null
        || media.getPurpose() != MediaPurpose.USER_PROFILE
        || media.getCompanyId() != null
        || media.getOwnerUserId() != ownerUserId
        || media.getStatus() == MediaStatus.DELETED) {
      return;
    }
    if (media.getStatus() == MediaStatus.ACTIVE) {
      storageAccounting.release(media);
      media.setStatus(MediaStatus.DELETE_PENDING);
      mediaRepository.saveAndFlush(media);
    }
    try {
      storageProviderResolver
          .resolve(media.getStorageProvider())
          .delete(media.getBucket(), media.getObjectKey());
      markDeleted(media.getId());
    } catch (RuntimeException ignored) {
      // Best effort: keep DELETE_PENDING rather than fail a profile update.
    }
  }

  @Transactional
  public void retireCompanyLogo(long mediaId, long companyId) {
    Media media = mediaRepository.findById(mediaId).orElse(null);
    if (media == null
        || media.getPurpose() != MediaPurpose.COMPANY_LOGO
        || !Long.valueOf(companyId).equals(media.getCompanyId())
        || media.getStatus() == MediaStatus.DELETED) {
      return;
    }
    if (media.getStatus() == MediaStatus.ACTIVE) {
      storageAccounting.release(media);
      media.setStatus(MediaStatus.DELETE_PENDING);
      mediaRepository.saveAndFlush(media);
    }
    try {
      storageProviderResolver
          .resolve(media.getStorageProvider())
          .delete(media.getBucket(), media.getObjectKey());
      markDeleted(media.getId());
    } catch (RuntimeException ignored) {
      // Best effort: keep DELETE_PENDING rather than fail the company profile update.
    }
  }

  @Transactional
  public MediaResponse delete(String mediaId) {
    long userId = currentUserService.currentUserId();
    Media media = loadOwnedMedia(parseMediaId(mediaId), userId);
    if (media.getStatus() == MediaStatus.DELETED) {
      return mediaMapper.toResponse(media);
    }
    if (media.getStatus() != MediaStatus.DELETE_PENDING) {
      storageAccounting.release(media);
      media.setStatus(MediaStatus.DELETE_PENDING);
      mediaRepository.saveAndFlush(media);
    }

    storageProviderResolver
        .resolve(media.getStorageProvider())
        .delete(media.getBucket(), media.getObjectKey());

    return mediaMapper.toResponse(markDeleted(media.getId()));
  }

  @Transactional(readOnly = true)
  public Media loadOwnedMedia(long mediaId, long userId) {
    Media media =
        mediaRepository
            .findById(mediaId)
            .orElseThrow(() -> new MediaException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND"));
    authorizationService.requireOwner(media, userId);
    return media;
  }

  @Transactional(readOnly = true)
  public Media getActiveOwnedMedia(long mediaId, long userId) {
    Media media = loadOwnedMedia(mediaId, userId);
    if (media.getStatus() != MediaStatus.ACTIVE) {
      throw new MediaException(HttpStatus.CONFLICT, "MEDIA_NOT_ACTIVE");
    }
    return media;
  }

  @Transactional
  protected Media markActive(long mediaId, Long ownerUserId, String objectKey, String checksum) {
    Media media =
        mediaRepository
            .findById(mediaId)
            .orElseThrow(() -> new MediaException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND"));
    if (media.getStatus() == MediaStatus.ACTIVE) {
      return media;
    }
    if (media.getStatus() != MediaStatus.PENDING) {
      throw new MediaException(HttpStatus.CONFLICT, "MEDIA_INVALID_STATE");
    }
    if (objectKey != null) {
      media.setObjectKey(objectKey);
    }
    if (ownerUserId != null) {
      media.setOwnerUserId(ownerUserId);
    }
    media.setStatus(MediaStatus.ACTIVE);
    media.setCompletedAt(Instant.now());
    media.setChecksum(checksum);
    return mediaRepository.saveAndFlush(media);
  }

  @Transactional
  protected void markFailed(long mediaId) {
    mediaRepository
        .findById(mediaId)
        .filter(media -> media.getStatus() == MediaStatus.PENDING)
        .ifPresent(
            media -> {
              storageAccounting.release(media);
              media.setStatus(MediaStatus.FAILED);
              mediaRepository.saveAndFlush(media);
            });
  }

  @Transactional
  protected Media markDeleted(long mediaId) {
    Media media =
        mediaRepository
            .findById(mediaId)
            .orElseThrow(() -> new MediaException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND"));
    media.setStatus(MediaStatus.DELETED);
    media.setDeletedAt(Instant.now());
    return mediaRepository.saveAndFlush(media);
  }

  private void validateCompanyScope(
      long userId, InitiateMediaUploadRequest request, long sizeBytes) {
    boolean companyPurpose =
        switch (request.purpose()) {
          case MESSAGE_ATTACHMENT, EMAIL_ATTACHMENT, WHATSAPP_MEDIA, TEMPLATE_MEDIA, AD_CREATIVE ->
              true;
          case USER_PROFILE, USER_COVER, COMPANY_LOGO, OTHER -> false;
        };

    if (companyPurpose && request.companyId() == null) {
      throw new MediaException(HttpStatus.BAD_REQUEST, "MEDIA_COMPANY_REQUIRED");
    }

    if (request.companyId() != null) {
      companyAuthorizationService.requirePermission(
          userId, request.companyId(), CompanyPermissionKeys.MEDIA_UPLOAD);
    }
  }

  private String finalObjectKey(Media media, long ownerUserId) {
    return objectKeyFactory.createUserObjectKey(
        ownerUserId, media.getPurpose(), media.getId(), extension(media.getObjectKey()));
  }

  @Transactional(readOnly = true)
  public java.util.List<MediaResponse> listCompanyMedia(Long companyId) {
    long userId = currentUserService.currentUserId();
    companyAuthorizationService.requirePermission(userId, companyId, CompanyPermissionKeys.MEDIA_READ);
    return mediaRepository
        .findByCompanyIdAndStatusOrderByCreatedAtDesc(
            companyId,
            MediaStatus.ACTIVE,
            org.springframework.data.domain.PageRequest.of(0, 100))
        .stream()
        .map(mediaMapper::toResponse)
        .toList();
  }

  @Transactional
  public Media ingestTrustedCompanyMedia(
      long companyId,
      long ownerUserId,
      String fileName,
      String contentType,
      byte[] content) {
    return ingestTrustedCompanyMediaWithId(
        IdGenerator.nextId(), companyId, ownerUserId, fileName, contentType, content);
  }

  @Transactional
  public Media ingestTrustedCompanyMediaWithId(
      long reservedMediaId,
      long companyId,
      long ownerUserId,
      String fileName,
      String contentType,
      byte[] content) {
    if (content == null || content.length == 0) {
      throw new MediaException(HttpStatus.BAD_REQUEST, "MEDIA_INVALID_SIZE");
    }
    MediaValidationService.ValidatedMedia validated =
        validationService.validateForUpload(fileName, contentType, content.length);
    String expectedObjectKey =
        objectKeyFactory.createUserObjectKey(
            ownerUserId, MediaPurpose.WHATSAPP_MEDIA, reservedMediaId, validated.extension());

    Media media = mediaRepository.findById(reservedMediaId).orElse(null);
    if (media != null) {
      if (!Long.valueOf(companyId).equals(media.getCompanyId())
          || media.getOwnerUserId() != ownerUserId
          || media.getPurpose() != MediaPurpose.WHATSAPP_MEDIA
          || !expectedObjectKey.equals(media.getObjectKey())
          || !validated.contentType().equals(media.getContentType())
          || validated.sizeBytes() != media.getSizeBytes()) {
        throw new MediaException(HttpStatus.CONFLICT, "MEDIA_IDEMPOTENCY_CONFLICT");
      }
      if (media.getStatus() == MediaStatus.ACTIVE) {
        return media;
      }
      if (media.getStatus() == MediaStatus.DELETE_PENDING
          || media.getStatus() == MediaStatus.DELETED) {
        throw new MediaException(HttpStatus.CONFLICT, "MEDIA_INVALID_STATE");
      }
      if (media.getStatus() == MediaStatus.FAILED) {
        media.setStatus(MediaStatus.PENDING);
        media.setCompletedAt(null);
        media.setDeletedAt(null);
        media.setChecksum(null);
        media = mediaRepository.saveAndFlush(media);
        storageAccounting.reserve(media);
      }
    } else {
      media = new Media();
      media.setId(reservedMediaId);
      media.setOwnerUserId(ownerUserId);
      media.setCompanyId(companyId);
      media.setStorageProvider(storageProperties.getDefaultProvider());
      media.setBucket(configuredBucket());
      media.setObjectKey(expectedObjectKey);
      media.setOriginalFileName(validated.fileName());
      media.setContentType(validated.contentType());
      media.setSizeBytes(validated.sizeBytes());
      media.setMediaType(validated.mediaType());
      media.setPurpose(MediaPurpose.WHATSAPP_MEDIA);
      media.setStatus(MediaStatus.PENDING);
      media = mediaRepository.saveAndFlush(media);
      storageAccounting.reserve(media);
    }

    StorageProvider provider = storageProviderResolver.resolve(media.getStorageProvider());
    boolean wroteObject = false;
    try {
      StorageObjectMetadata metadata =
          provider.getObjectMetadata(media.getBucket(), media.getObjectKey());
      if (metadata.exists()) {
        try {
          validationService.validateUploadedObject(
              media.getContentType(),
              media.getSizeBytes(),
              metadata.contentType(),
              metadata.sizeBytes());
        } catch (MediaException staleObject) {
          provider.writeObject(
              media.getBucket(), media.getObjectKey(), content, media.getContentType());
          wroteObject = true;
          metadata = provider.getObjectMetadata(media.getBucket(), media.getObjectKey());
        }
      } else {
        provider.writeObject(
            media.getBucket(), media.getObjectKey(), content, media.getContentType());
        wroteObject = true;
        metadata = provider.getObjectMetadata(media.getBucket(), media.getObjectKey());
      }
      if (!metadata.exists()) {
        throw new MediaException(HttpStatus.SERVICE_UNAVAILABLE, "MEDIA_STORAGE_ERROR");
      }
      validationService.validateUploadedObject(
          media.getContentType(),
          media.getSizeBytes(),
          metadata.contentType(),
          metadata.sizeBytes());
      media.setStatus(MediaStatus.ACTIVE);
      media.setCompletedAt(Instant.now());
      media.setChecksum(metadata.checksum());
      media = mediaRepository.saveAndFlush(media);
      storageAccounting.commit(media);
      return media;
    } catch (RuntimeException exception) {
      if (wroteObject) {
        try {
          provider.delete(media.getBucket(), media.getObjectKey());
        } catch (RuntimeException ignored) {
          // Cleanup is best effort. A crash/retry reuses the same reserved object key.
        }
      }
      if (media.getStatus() == MediaStatus.PENDING) {
        storageAccounting.release(media);
        media.setStatus(MediaStatus.FAILED);
        mediaRepository.saveAndFlush(media);
      }
      throw exception;
    }
  }

  @Transactional(readOnly = true)
  public Optional<Media> findActiveTrustedCompanyMedia(long mediaId, long companyId) {
    return mediaRepository
        .findByIdAndCompanyId(mediaId, companyId)
        .filter(media -> media.getStatus() == MediaStatus.ACTIVE);
  }

  @Transactional(readOnly = true)
  public Media requireActiveCompanyMedia(long mediaId, long companyId) {
    Media media =
        mediaRepository
            .findById(mediaId)
            .orElseThrow(() -> new MediaException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND"));
    if (media.getCompanyId() == null || !media.getCompanyId().equals(companyId)) {
      throw new MediaException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND");
    }
    if (media.getStatus() != MediaStatus.ACTIVE) {
      throw new MediaException(HttpStatus.CONFLICT, "MEDIA_NOT_ACTIVE");
    }
    return media;
  }

  @Transactional(readOnly = true)
  public InputStream openTrustedObject(long mediaId, long companyId) {
    Media media = requireActiveCompanyMedia(mediaId, companyId);
    return storageProviderResolver
        .resolve(media.getStorageProvider())
        .openObject(media.getBucket(), media.getObjectKey());
  }

  @Transactional(readOnly = true)
  public byte[] readTrustedObject(long mediaId, long companyId) {
    Media media = requireActiveCompanyMedia(mediaId, companyId);
    return storageProviderResolver
        .resolve(media.getStorageProvider())
        .readObject(media.getBucket(), media.getObjectKey());
  }

  @Transactional(readOnly = true)
  public MediaAccessResponse createTrustedAccessUrl(long mediaId) {
    Media media =
        mediaRepository
            .findById(mediaId)
            .orElseThrow(() -> new MediaException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND"));
    if (media.getStatus() != MediaStatus.ACTIVE) {
      throw new MediaException(HttpStatus.CONFLICT, "MEDIA_NOT_ACTIVE");
    }
    StorageProvider provider = storageProviderResolver.resolve(media.getStorageProvider());
    SignedDownloadResult signedDownload =
        provider.createSignedDownloadUrl(
            media.getBucket(), media.getObjectKey(), storageProperties.getDownloadUrlExpiry());
    return new MediaAccessResponse(signedDownload.url(), signedDownload.expiresAt());
  }

  private String configuredBucket() {
    String bucket =
        switch (storageProperties.getDefaultProvider()) {
          case SUPABASE -> storageProperties.getSupabase().getBucket();
          case S3 -> storageProperties.getS3().getBucket();
        };
    if (!org.springframework.util.StringUtils.hasText(bucket)) {
      throw new MediaException(HttpStatus.SERVICE_UNAVAILABLE, "MEDIA_STORAGE_NOT_CONFIGURED");
    }
    return bucket.trim();
  }

  private String secureToken() {
    byte[] bytes = new byte[32];
    secureRandom.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private boolean tokenMatches(String expected, String actual) {
    if (expected == null || actual == null) {
      return false;
    }
    return MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
  }

  private String extension(String objectKey) {
    int dotIndex = objectKey.lastIndexOf('.');
    if (dotIndex < 0 || dotIndex == objectKey.length() - 1) {
      throw new MediaException(HttpStatus.BAD_REQUEST, "MEDIA_INVALID_EXTENSION");
    }
    return objectKey.substring(dotIndex + 1);
  }

  private long parseMediaId(String mediaId) {
    try {
      return Long.parseLong(mediaId);
    } catch (NumberFormatException exception) {
      throw new MediaException(HttpStatus.BAD_REQUEST, "MEDIA_INVALID_ID");
    }
  }
}
