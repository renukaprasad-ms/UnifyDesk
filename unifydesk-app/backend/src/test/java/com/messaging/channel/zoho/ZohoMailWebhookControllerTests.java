package com.messaging.channel.zoho;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.mockito.Mockito;

class ZohoMailWebhookControllerTests {

  private final ZohoMailWebhookService service = Mockito.mock(ZohoMailWebhookService.class);
  private final ZohoMailWebhookController controller = new ZohoMailWebhookController(service);

  @Test
  void signedWebhookIsAcknowledgedOnlyAfterQueueWrite() {
    byte[] body = "{\"messageId\":123}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    when(service.authenticateAndQueue(eq(42L), eq("private-token"),
        eq("signature"), eq("first-secret"), eq(body))).thenReturn(true);

    ResponseEntity<Void> response =
        controller.receive(42L, "private-token", "signature", "first-secret", body);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void invalidWebhookIsRejectedAndStorageFailuresAreRetryable() {
    byte[] body = "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    ResponseEntity<Void> denied = controller.receive(42L, "bad", "wrong", null, body);
    assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

    when(service.authenticateAndQueue(eq(42L), eq("token"), eq("signature"),
        eq("secret"), eq(body))).thenThrow(new IllegalStateException("database failure"));
    ResponseEntity<Void> unavailable =
        controller.receive(42L, "token", "signature", "secret", body);
    assertThat(unavailable.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
  }
}
