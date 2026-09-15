package com.messaging.media.config;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.media.validation")
public class MediaValidationProperties {

  @NotNull private DataSize imageMaxSize;
  @NotNull private DataSize documentMaxSize;
  @NotNull private DataSize videoMaxSize;
  @NotNull private DataSize audioMaxSize;
  @NotNull private DataSize otherMaxSize;
  @NotNull private Duration pendingTtl;
  @NotEmpty private Set<String> allowedMimeTypes;
}
