package com.messaging.media.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.messaging.media.enums.StorageProviderType;
import com.messaging.media.exception.MediaException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class StorageProviderResolverTests {

  @Test
  void resolvesSupabaseProvider() {
    StorageProvider supabase = new NoopProvider(StorageProviderType.SUPABASE);

    StorageProviderResolver resolver = new StorageProviderResolver(List.of(supabase));

    assertThat(resolver.resolve(StorageProviderType.SUPABASE)).isSameAs(supabase);
  }

  @Test
  void unknownProviderFailsClearly() {
    StorageProviderResolver resolver = new StorageProviderResolver(List.of());

    assertThatThrownBy(() -> resolver.resolve(StorageProviderType.S3))
        .isInstanceOf(MediaException.class)
        .hasMessage("MEDIA_STORAGE_PROVIDER_UNAVAILABLE");
  }

  private record NoopProvider(StorageProviderType type) implements StorageProvider {

    @Override
    public SignedUploadResult createSignedUpload(SignedUploadRequest request) {
      throw new UnsupportedOperationException();
    }

    @Override
    public StorageObjectMetadata getObjectMetadata(String bucket, String objectKey) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void move(String bucket, String sourceObjectKey, String destinationObjectKey) {
      throw new UnsupportedOperationException();
    }

    @Override
    public SignedDownloadResult createSignedDownloadUrl(
        String bucket, String objectKey, Duration expiration) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void delete(String bucket, String objectKey) {
      throw new UnsupportedOperationException();
    }
  }
}
