package com.messaging.channel.meta.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.messaging.media.config.MediaValidationProperties;
import com.messaging.media.exception.MediaException;
import com.messaging.media.service.MediaValidationService;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

class MetaWhatsAppMediaValidationTests {

  private MediaValidationService validation;

  @BeforeEach
  void setUp() {
    MediaValidationProperties properties = new MediaValidationProperties();
    properties.setAllowedMimeTypes(
        Set.of("image/jpeg", "image/png", "image/webp", "application/pdf"));
    properties.setImageMaxSize(DataSize.ofMegabytes(5));
    properties.setDocumentMaxSize(DataSize.ofMegabytes(20));
    properties.setVideoMaxSize(DataSize.ofMegabytes(1));
    properties.setAudioMaxSize(DataSize.ofMegabytes(1));
    properties.setOtherMaxSize(DataSize.ofMegabytes(1));
    properties.setPendingTtl(Duration.ofMinutes(15));
    validation = new MediaValidationService(properties);
  }

  @Test
  void acceptsSupportedImageAndPdf() {
    var image = validation.validateForUpload("photo.jpg", "image/jpeg", 1024);
    var pdf = validation.validateForUpload("invoice.pdf", "application/pdf", 2048);

    assertThat(image.extension()).isEqualTo("jpg");
    assertThat(pdf.extension()).isEqualTo("pdf");
  }

  @Test
  void rejectsUnsupportedMimeAndOversizedDocument() {
    assertThatThrownBy(
            () -> validation.validateForUpload("script.svg", "image/svg+xml", 100))
        .isInstanceOf(MediaException.class);
    assertThatThrownBy(
            () ->
                validation.validateForUpload(
                    "huge.pdf",
                    "application/pdf",
                    DataSize.ofMegabytes(21).toBytes()))
        .isInstanceOf(MediaException.class);
  }
}
