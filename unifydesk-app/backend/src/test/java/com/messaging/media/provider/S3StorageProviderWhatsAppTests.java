package com.messaging.media.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.messaging.media.config.MediaStorageProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

class S3StorageProviderWhatsAppTests {

  @Test
  void trustedServerWriteUsesPrivateConfiguredObjectPathAndContentType() {
    MediaStorageProperties properties = new MediaStorageProperties();
    properties.getS3().setBucket("private-media");
    properties.getS3().setRegion("ap-south-1");
    S3StorageProvider provider = new S3StorageProvider(properties);
    S3Client client = mock(S3Client.class);
    ReflectionTestUtils.setField(provider, "client", client);

    provider.writeObject(
        "private-media",
        "users/7/whatsapp/42/document.pdf",
        "pdf-bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8),
        "application/pdf");

    ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
    ArgumentCaptor<RequestBody> body = ArgumentCaptor.forClass(RequestBody.class);
    verify(client).putObject(request.capture(), body.capture());
    assertThat(request.getValue().bucket()).isEqualTo("private-media");
    assertThat(request.getValue().key()).isEqualTo("users/7/whatsapp/42/document.pdf");
    assertThat(request.getValue().contentType()).isEqualTo("application/pdf");
    assertThat(body.getValue().contentLength()).isEqualTo(9L);
  }
}
