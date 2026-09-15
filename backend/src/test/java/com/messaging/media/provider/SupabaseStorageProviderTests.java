package com.messaging.media.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.messaging.media.config.MediaStorageProperties;
import com.messaging.media.exception.MediaException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class SupabaseStorageProviderTests {

  private MockRestServiceServer server;
  private SupabaseStorageProvider provider;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder();
    server = MockRestServiceServer.bindTo(builder).build();
    MediaStorageProperties properties = new MediaStorageProperties();
    properties.getSupabase().setBaseUrl("https://project.supabase.co");
    properties.getSupabase().setServiceRoleKey("service-role");
    properties.getSupabase().setBucket("private-media");
    provider = new SupabaseStorageProvider(properties, builder.build());
  }

  @Test
  void createsSignedUploadUrl() {
    server
        .expect(
            once(),
            requestTo(
                "https://project.supabase.co/storage/v1/object/upload/sign/private-media/users/1/profile/2.jpg"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("apikey", "service-role"))
        .andRespond(
            withSuccess(
                "{\"url\":\"/object/upload/sign/private-media/users/1/profile/2.jpg?token=abc\"}",
                MediaType.APPLICATION_JSON));

    SignedUploadResult result =
        provider.createSignedUpload(
            new SignedUploadRequest(
                "private-media",
                "users/1/profile/2.jpg",
                "image/jpeg",
                100L,
                Duration.ofMinutes(10)));

    assertThat(result.uploadUrl())
        .isEqualTo(
            "https://project.supabase.co/storage/v1/object/upload/sign/private-media/users/1/profile/2.jpg?token=abc");
    assertThat(result.requiredHeaders()).containsEntry("content-type", "image/jpeg");
    server.verify();
  }

  @Test
  void createsSignedDownloadUrl() {
    server
        .expect(
            once(),
            requestTo(
                "https://project.supabase.co/storage/v1/object/sign/private-media/users/1/profile/2.jpg"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(
            withSuccess(
                "{\"signedURL\":\"/object/sign/private-media/users/1/profile/2.jpg?token=abc\"}",
                MediaType.APPLICATION_JSON));

    SignedDownloadResult result =
        provider.createSignedDownloadUrl(
            "private-media", "users/1/profile/2.jpg", Duration.ofMinutes(5));

    assertThat(result.url())
        .isEqualTo(
            "https://project.supabase.co/storage/v1/object/sign/private-media/users/1/profile/2.jpg?token=abc");
    server.verify();
  }

  @Test
  void looksUpMetadata() {
    server
        .expect(
            once(),
            requestTo(
                "https://project.supabase.co/storage/v1/object/authenticated/private-media/users/1/profile/2.jpg"))
        .andExpect(method(HttpMethod.HEAD))
        .andRespond(
            withSuccess()
                .contentType(MediaType.IMAGE_JPEG)
                .header("Content-Length", "100")
                .header("ETag", "checksum"));

    StorageObjectMetadata metadata =
        provider.getObjectMetadata("private-media", "users/1/profile/2.jpg");

    assertThat(metadata.exists()).isTrue();
    assertThat(metadata.contentType()).isEqualTo("image/jpeg");
    assertThat(metadata.sizeBytes()).isEqualTo(100L);
    assertThat(metadata.checksum()).isEqualTo("checksum");
    server.verify();
  }

  @Test
  void deletesObject() {
    server
        .expect(
            once(),
            requestTo(
                "https://project.supabase.co/storage/v1/object/private-media/users/1/profile/2.jpg"))
        .andExpect(method(HttpMethod.DELETE))
        .andRespond(withNoContent());

    provider.delete("private-media", "users/1/profile/2.jpg");

    server.verify();
  }

  @Test
  void movesObject() {
    server
        .expect(once(), requestTo("https://project.supabase.co/storage/v1/object/move"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

    provider.move("private-media", "tmp/uploads/1/2.jpg", "users/1/profile/2.jpg");

    server.verify();
  }

  @Test
  void providerErrorsAreMapped() {
    server
        .expect(
            once(),
            requestTo(
                "https://project.supabase.co/storage/v1/object/sign/private-media/missing.jpg"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withServerError());

    assertThatThrownBy(
            () ->
                provider.createSignedDownloadUrl(
                    "private-media", "missing.jpg", Duration.ofMinutes(5)))
        .isInstanceOf(MediaException.class)
        .hasMessage("MEDIA_STORAGE_ERROR");
    server.verify();
  }
}
