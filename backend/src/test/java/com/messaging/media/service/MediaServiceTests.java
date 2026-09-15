package com.messaging.media.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.messaging.media.config.MediaStorageProperties;
import com.messaging.media.config.MediaValidationProperties;
import com.messaging.media.dto.InitiateMediaUploadRequest;
import com.messaging.media.dto.InitiateMediaUploadResponse;
import com.messaging.media.entity.Media;
import com.messaging.media.enums.MediaPurpose;
import com.messaging.media.enums.MediaStatus;
import com.messaging.media.enums.MediaType;
import com.messaging.media.enums.StorageProviderType;
import com.messaging.media.exception.MediaException;
import com.messaging.media.mapper.MediaMapper;
import com.messaging.media.provider.SignedDownloadResult;
import com.messaging.media.provider.SignedUploadRequest;
import com.messaging.media.provider.SignedUploadResult;
import com.messaging.media.provider.StorageObjectMetadata;
import com.messaging.media.provider.StorageProvider;
import com.messaging.media.provider.StorageProviderResolver;
import com.messaging.media.repository.MediaRepository;
import com.messaging.redis.service.RedisService;
import com.messaging.security.service.CurrentUserService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MediaServiceTests {

  @Mock private MediaRepository mediaRepository;
  @Mock private CurrentUserService currentUserService;
  @Mock private StorageProvider storageProvider;
  @Mock private RedisService redisService;

  private MediaService service;

  @BeforeEach
  void setUp() {
    MediaStorageProperties storageProperties = new MediaStorageProperties();
    storageProperties.setDefaultProvider(StorageProviderType.SUPABASE);
    storageProperties.setUploadUrlExpiry(Duration.ofMinutes(10));
    storageProperties.setDownloadUrlExpiry(Duration.ofMinutes(5));
    storageProperties.getSupabase().setBucket("private-media");
    MediaValidationProperties validationProperties = new MediaValidationProperties();
    validationProperties.setImageMaxSize(org.springframework.util.unit.DataSize.ofMegabytes(10));
    validationProperties.setDocumentMaxSize(org.springframework.util.unit.DataSize.ofMegabytes(25));
    validationProperties.setVideoMaxSize(org.springframework.util.unit.DataSize.ofMegabytes(100));
    validationProperties.setAudioMaxSize(org.springframework.util.unit.DataSize.ofMegabytes(25));
    validationProperties.setOtherMaxSize(org.springframework.util.unit.DataSize.ofMegabytes(10));
    validationProperties.setPendingTtl(Duration.ofHours(24));
    validationProperties.setAllowedMimeTypes(
        Set.of("image/jpeg", "image/png", "image/webp", "application/pdf"));

    when(storageProvider.type()).thenReturn(StorageProviderType.SUPABASE);
    service =
        new TestMediaService(
            mediaRepository,
            currentUserService,
            new MediaValidationService(validationProperties),
            new MediaObjectKeyFactory(),
            new StorageProviderResolver(List.of(storageProvider)),
            storageProperties,
            validationProperties,
            new MediaAuthorizationService(),
            new MediaMapper(),
            redisService);
  }

  @Test
  void userCanInitiateUploadAndPendingMediaIsCreated() {
    when(currentUserService.currentUserIdOrEmpty()).thenReturn(Optional.of(10L));
    when(mediaRepository.saveAndFlush(any(Media.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(storageProvider.createSignedUpload(any(SignedUploadRequest.class)))
        .thenReturn(
            new SignedUploadResult(
                "https://upload.example", Instant.now().plusSeconds(600), Map.of()));

    InitiateMediaUploadResponse response =
        service.initiateUpload(
            new InitiateMediaUploadRequest(
                "avatar.jpg", "image/jpeg", 203942L, MediaPurpose.USER_PROFILE));

    ArgumentCaptor<Media> mediaCaptor = ArgumentCaptor.forClass(Media.class);
    verify(mediaRepository).saveAndFlush(mediaCaptor.capture());
    Media media = mediaCaptor.getValue();
    assertThat(response.mediaId()).isEqualTo(media.getId().toString());
    assertThat(media.getStatus()).isEqualTo(MediaStatus.PENDING);
    assertThat(media.getOwnerUserId()).isEqualTo(10L);
    assertThat(media.getObjectKey()).matches("tmp/uploads/10/\\d+\\.jpg");
  }

  @Test
  void invalidMimeIsRejected() {
    assertThatThrownBy(
            () ->
                service.initiateUpload(
                    new InitiateMediaUploadRequest(
                        "avatar.gif", "image/gif", 100L, MediaPurpose.USER_PROFILE)))
        .isInstanceOf(MediaException.class)
        .hasMessage("MEDIA_INVALID_TYPE");
    verify(mediaRepository, never()).saveAndFlush(any());
  }

  @Test
  void oversizedFileIsRejected() {
    assertThatThrownBy(
            () ->
                service.initiateUpload(
                    new InitiateMediaUploadRequest(
                        "avatar.jpg",
                        "image/jpeg",
                        11L * 1024L * 1024L,
                        MediaPurpose.USER_PROFILE)))
        .isInstanceOf(MediaException.class)
        .hasMessage("MEDIA_TOO_LARGE");
  }

  @Test
  void completeUploadVerifiesMetadataAndActivatesPendingMedia() {
    Media media = pendingMedia(100L, 10L);
    when(currentUserService.currentUserId()).thenReturn(10L);
    when(mediaRepository.findById(100L)).thenReturn(Optional.of(media));
    when(storageProvider.getObjectMetadata("private-media", "tmp/uploads/10/100.jpg"))
        .thenReturn(new StorageObjectMetadata(true, "image/jpeg", 100L, "etag"));
    when(mediaRepository.saveAndFlush(any(Media.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    assertThat(service.completeUpload("100").status()).isEqualTo(MediaStatus.ACTIVE);

    assertThat(media.getCompletedAt()).isNotNull();
    assertThat(media.getChecksum()).isEqualTo("etag");
    assertThat(media.getObjectKey()).isEqualTo("users/10/profile/100.jpg");
    verify(storageProvider)
        .move("private-media", "tmp/uploads/10/100.jpg", "users/10/profile/100.jpg");
  }

  @Test
  void completeUploadAcceptsGenericStorageContentTypeWhenSizeMatches() {
    Media media = pendingMedia(100L, 10L);
    when(currentUserService.currentUserId()).thenReturn(10L);
    when(mediaRepository.findById(100L)).thenReturn(Optional.of(media));
    when(storageProvider.getObjectMetadata("private-media", "tmp/uploads/10/100.jpg"))
        .thenReturn(new StorageObjectMetadata(true, "application/octet-stream", 100L, "etag"));
    when(mediaRepository.saveAndFlush(any(Media.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    assertThat(service.completeUpload("100").status()).isEqualTo(MediaStatus.ACTIVE);

    assertThat(media.getObjectKey()).isEqualTo("users/10/profile/100.jpg");
  }

  @Test
  void anotherUserCannotCompleteMedia() {
    when(currentUserService.currentUserId()).thenReturn(11L);
    when(mediaRepository.findById(100L)).thenReturn(Optional.of(pendingMedia(100L, 10L)));

    assertThatThrownBy(() -> service.completeUpload("100"))
        .isInstanceOf(MediaException.class)
        .hasMessage("MEDIA_ACCESS_DENIED");
  }

  @Test
  void anotherUserCannotAccessMedia() {
    Media media = activeMedia(100L, 10L);
    when(currentUserService.currentUserId()).thenReturn(11L);
    when(mediaRepository.findById(100L)).thenReturn(Optional.of(media));

    assertThatThrownBy(() -> service.createAccessUrl("100"))
        .isInstanceOf(MediaException.class)
        .hasMessage("MEDIA_ACCESS_DENIED");
  }

  @Test
  void inactiveMediaCannotGenerateDownloadUrl() {
    when(currentUserService.currentUserId()).thenReturn(10L);
    when(mediaRepository.findById(100L)).thenReturn(Optional.of(pendingMedia(100L, 10L)));

    assertThatThrownBy(() -> service.createAccessUrl("100"))
        .isInstanceOf(MediaException.class)
        .hasMessage("MEDIA_NOT_ACTIVE");
  }

  @Test
  void activeMediaGeneratesDownloadUrl() {
    when(currentUserService.currentUserId()).thenReturn(10L);
    when(mediaRepository.findById(100L)).thenReturn(Optional.of(activeMedia(100L, 10L)));
    when(storageProvider.createSignedDownloadUrl(any(), any(), any()))
        .thenReturn(
            new SignedDownloadResult("https://download.example", Instant.now().plusSeconds(60)));

    assertThat(service.createAccessUrl("100").url()).isEqualTo("https://download.example");
  }

  @Test
  void deletingMediaChangesState() {
    Media media = activeMedia(100L, 10L);
    when(currentUserService.currentUserId()).thenReturn(10L);
    when(mediaRepository.findById(100L)).thenReturn(Optional.of(media));
    when(mediaRepository.saveAndFlush(any(Media.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    assertThat(service.delete("100").status()).isEqualTo(MediaStatus.DELETED);
    assertThat(media.getDeletedAt()).isNotNull();
    verify(storageProvider).delete("private-media", "users/10/profile/100.jpg");
  }

  private Media pendingMedia(long mediaId, long ownerUserId) {
    Media media = new Media();
    media.setId(mediaId);
    media.setOwnerUserId(ownerUserId);
    media.setStorageProvider(StorageProviderType.SUPABASE);
    media.setBucket("private-media");
    media.setObjectKey("tmp/uploads/%d/%d.jpg".formatted(ownerUserId, mediaId));
    media.setOriginalFileName("avatar.jpg");
    media.setContentType("image/jpeg");
    media.setSizeBytes(100L);
    media.setMediaType(MediaType.IMAGE);
    media.setPurpose(MediaPurpose.USER_PROFILE);
    media.setStatus(MediaStatus.PENDING);
    media.setCreatedAt(Instant.now());
    return media;
  }

  private Media activeMedia(long mediaId, long ownerUserId) {
    Media media = pendingMedia(mediaId, ownerUserId);
    media.setObjectKey("users/%d/profile/%d.jpg".formatted(ownerUserId, mediaId));
    media.setStatus(MediaStatus.ACTIVE);
    media.setCompletedAt(Instant.now());
    return media;
  }

  private static class TestMediaService extends MediaService {

    private final MediaRepository mediaRepository;

    TestMediaService(
        MediaRepository mediaRepository,
        CurrentUserService currentUserService,
        MediaValidationService validationService,
        MediaObjectKeyFactory objectKeyFactory,
        StorageProviderResolver storageProviderResolver,
        MediaStorageProperties storageProperties,
        MediaValidationProperties validationProperties,
        MediaAuthorizationService authorizationService,
        MediaMapper mediaMapper,
        RedisService redisService) {
      super(
          mediaRepository,
          currentUserService,
          validationService,
          objectKeyFactory,
          storageProviderResolver,
          storageProperties,
          validationProperties,
          authorizationService,
          mediaMapper,
          redisService);
      this.mediaRepository = mediaRepository;
    }

    @Override
    protected Media createPendingMedia(
        long userId,
        InitiateMediaUploadRequest request,
        MediaValidationService.ValidatedMedia validated) {
      Media media = new Media();
      media.setId(123456789L);
      media.setOwnerUserId(userId);
      media.setStorageProvider(StorageProviderType.SUPABASE);
      media.setBucket("private-media");
      media.setObjectKey(
          new MediaObjectKeyFactory()
              .createTemporaryObjectKey(userId, media.getId(), validated.extension()));
      media.setOriginalFileName(validated.fileName());
      media.setContentType(validated.contentType());
      media.setSizeBytes(validated.sizeBytes());
      media.setMediaType(validated.mediaType());
      media.setPurpose(request.purpose());
      media.setStatus(MediaStatus.PENDING);
      media.setCreatedAt(Instant.now());
      return mediaRepository.saveAndFlush(media);
    }
  }
}
