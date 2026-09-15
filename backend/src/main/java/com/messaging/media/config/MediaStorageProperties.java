package com.messaging.media.config;

import com.messaging.media.enums.StorageProviderType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.media.storage")
public class MediaStorageProperties {

  @NotNull private StorageProviderType defaultProvider;
  @NotNull private Duration uploadUrlExpiry;
  @NotNull private Duration downloadUrlExpiry;
  @NotNull private Duration httpConnectTimeout = Duration.ofSeconds(3);
  @NotNull private Duration httpReadTimeout = Duration.ofSeconds(5);
  @Valid @NotNull private Supabase supabase = new Supabase();

  @Getter
  @Setter
  public static class Supabase {
    @NotBlank private String baseUrl;
    @NotBlank private String serviceRoleKey;
    @NotBlank private String bucket;
  }
}
