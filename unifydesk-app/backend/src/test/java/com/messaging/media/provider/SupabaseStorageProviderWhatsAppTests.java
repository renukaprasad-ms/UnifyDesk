package com.messaging.media.provider;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.messaging.media.config.MediaStorageProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class SupabaseStorageProviderWhatsAppTests {

  @Test
  void trustedServerWriteUsesAuthenticatedPrivateObjectEndpoint() {
    MediaStorageProperties properties = new MediaStorageProperties();
    properties.getSupabase().setBaseUrl("https://project.supabase.co");
    properties.getSupabase().setServiceRoleKey("server-only-key");

    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    SupabaseStorageProvider provider =
        new SupabaseStorageProvider(properties, builder.build());

    server
        .expect(
            requestTo(
                "https://project.supabase.co/storage/v1/object/private-media/users/7/whatsapp/42/image.jpg"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("apikey", "server-only-key"))
        .andExpect(header("Authorization", "Bearer server-only-key"))
        .andExpect(header("x-upsert", "false"))
        .andExpect(header("Content-Type", MediaType.IMAGE_JPEG_VALUE))
        .andRespond(withSuccess());

    provider.writeObject(
        "private-media",
        "users/7/whatsapp/42/image.jpg",
        new byte[] {1, 2, 3},
        MediaType.IMAGE_JPEG_VALUE);

    server.verify();
  }
}
