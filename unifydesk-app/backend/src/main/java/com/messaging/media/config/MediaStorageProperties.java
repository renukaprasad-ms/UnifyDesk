package com.messaging.media.config;

import com.messaging.media.enums.StorageProviderType;
import jakarta.validation.Valid;
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
  @Valid @NotNull private S3 s3 = new S3();

  @Getter
  @Setter
  public static class Supabase {
    private String baseUrl;
    private String serviceRoleKey;
    private String bucket;
  }

  @Getter
  @Setter
  public static class S3 {
    private String region;
    private String bucket;
    private String endpoint;
    private String accessKeyId;
    private String secretAccessKey;
    private boolean pathStyleAccessEnabled;
  }
}
