package com.messaging.contacts.consent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.messaging.common.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class RecipientActionControllerTests {

  @Test
  void publicUnsubscribeDoesNotRequireWorkspaceIdentity() {
    RecipientEmailUnsubscribeService service = mock(RecipientEmailUnsubscribeService.class);
    RecipientActionController controller = new RecipientActionController(service);

    var response = controller.unsubscribe("opaque");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    verify(service).unsubscribe("opaque");
  }

  @Test
  void replayReturnsSuccessEquivalentResponse() {
    RecipientEmailUnsubscribeService service = mock(RecipientEmailUnsubscribeService.class);
    RecipientActionController controller = new RecipientActionController(service);

    var first = controller.unsubscribe("opaque");
    var replay = controller.unsubscribe("opaque");

    assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
    verify(service, times(2)).unsubscribe("opaque");
  }

  @Test
  void invalidActionReturnsGenericNonEnumeratingError() {
    RecipientEmailUnsubscribeService service = mock(RecipientEmailUnsubscribeService.class);
    doThrow(new InvalidRecipientActionException()).when(service).unsubscribe("bad");
    RecipientActionController controller = new RecipientActionController(service);

    var response = controller.unsubscribe("bad");
    ApiResponse<?> body = response.getBody();

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(body).isNotNull();
    assertThat(body.getErrorMessage()).isEqualTo("Unsubscribe link is invalid or expired");
    assertThat(body.getErrorMessage()).doesNotContain("company", "contact", "identity");
  }
}
