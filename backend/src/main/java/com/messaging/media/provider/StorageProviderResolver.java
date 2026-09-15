package com.messaging.media.provider;

import com.messaging.media.enums.StorageProviderType;
import com.messaging.media.exception.MediaException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class StorageProviderResolver {

  private final Map<StorageProviderType, StorageProvider> providers;

  public StorageProviderResolver(List<StorageProvider> providerList) {
    this.providers = new EnumMap<>(StorageProviderType.class);
    for (StorageProvider provider : providerList) {
      StorageProvider existing = providers.put(provider.type(), provider);
      if (existing != null) {
        throw new IllegalStateException("Duplicate storage provider: " + provider.type());
      }
    }
  }

  public StorageProvider resolve(StorageProviderType type) {
    StorageProvider provider = providers.get(type);
    if (provider == null) {
      throw new MediaException(
          HttpStatus.SERVICE_UNAVAILABLE, "MEDIA_STORAGE_PROVIDER_UNAVAILABLE");
    }
    return provider;
  }
}
