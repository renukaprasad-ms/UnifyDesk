package com.messaging.channel.gmail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.messaging.common.exception.BadRequestException;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class GmailApiClientTests {

  @Test
  void messagesListUriEncodesSearchQueryExactlyOnce() {
    GmailOAuthProperties properties = new GmailOAuthProperties();
    GmailApiClient client = new GmailApiClient(properties);

    var uri = client.messagesListUri(100, "after:1787183999 before:1789862400", "next/page+token");

    assertThat(uri.getQuery())
        .contains("q=after:1787183999 before:1789862400")
        .contains("pageToken=next/page+token");
    assertThat(uri.toASCIIString())
        .contains("q=after:1787183999%20before:1789862400")
        .doesNotContain("%2520")
        .doesNotContain("%253A");
  }

  @Test
  void authorizationUrlRequestsOfflineGmailModifyAccessAndState() {
    GmailOAuthProperties properties = new GmailOAuthProperties();
    properties.setEnabled(true);
    properties.setClientId("client-id");
    properties.setClientSecret("client-secret");
    properties.setRedirectUri("https://api.example.com/api/v1/oauth/gmail/callback");
    properties.setFrontendBaseUrl("https://app.example.com");

    GmailApiClient client = new GmailApiClient(properties);

    String url = client.authorizationUrl("state-value");

    assertThat(url)
        .startsWith("https://accounts.google.com/o/oauth2/v2/auth?")
        .contains("client_id=client-id")
        .contains("response_type=code")
        .contains("access_type=offline")
        .contains("include_granted_scopes=true")
        .contains("prompt=consent")
        .contains("state=state-value")
        .contains("gmail.modify");
  }

  @Test
  void batchRequestBuildsOneMultipartPartPerMessage() {
    GmailOAuthProperties properties = new GmailOAuthProperties();
    properties.setSyncBatchSize(50);
    GmailApiClient client = new GmailApiClient(properties);

    List<String> ids = IntStream.range(0, 50).mapToObj(index -> "message-" + index).toList();
    String body = client.batchRequestBody("test-boundary", ids);

    assertThat(body)
        .contains("Content-ID: <gmail-0>")
        .contains("Content-ID: <gmail-49>")
        .contains("GET /gmail/v1/users/me/messages/message-0?format=full HTTP/1.1")
        .contains("GET /gmail/v1/users/me/messages/message-49?format=full HTTP/1.1")
        .endsWith("--test-boundary--\r\n");
    assertThat(body.split("GET /gmail/v1/users/me/messages/", -1)).hasSize(51);
  }

  @Test
  void batchParserAcceptsUnknownGmailFields() {
    GmailOAuthProperties properties = new GmailOAuthProperties();
    GmailApiClient client = new GmailApiClient(properties);

    String boundary = "batch_response";
    String response =
        "--" + boundary + "\r\n"
            + "Content-Type: application/http\r\n"
            + "Content-ID: <response-gmail-0>\r\n\r\n"
            + "HTTP/1.1 200 OK\r\n"
            + "Content-Type: application/json; charset=UTF-8\r\n\r\n"
            + "{\"id\":\"message-1\",\"threadId\":\"thread-1\","
            + "\"sizeEstimate\":1234,"
            + "\"payload\":{\"mimeType\":\"text/plain\",\"partId\":\"0\","
            + "\"headers\":[],\"body\":{\"size\":0},\"parts\":[]}}\r\n"
            + "--" + boundary + "--\r\n";

    var results =
        client.parseBatchResponse(
            response.getBytes(java.nio.charset.StandardCharsets.UTF_8),
            boundary,
            List.of("message-1"));

    assertThat(results).hasSize(1);
    assertThat(results.getFirst().successful()).isTrue();
    assertThat(results.getFirst().message().id()).isEqualTo("message-1");
    assertThat(results.getFirst().message().threadId()).isEqualTo("thread-1");
  }

  @Test
  void batchParserKeepsSuccessfulPartsAndRetriesMissingParts() {
    GmailOAuthProperties properties = new GmailOAuthProperties();
    GmailApiClient client = new GmailApiClient(properties);

    String boundary = "batch_response";
    String response =
        "--" + boundary + "\r\n"
            + "Content-Type: application/http\r\n"
            + "Content-ID: <response-gmail-0>\r\n\r\n"
            + "HTTP/1.1 200 OK\r\n"
            + "Content-Type: application/json\r\n\r\n"
            + "{\"id\":\"message-1\",\"threadId\":\"thread-1\"}\r\n"
            + "--" + boundary + "--\r\n";

    var results =
        client.parseBatchResponse(
            response.getBytes(java.nio.charset.StandardCharsets.UTF_8),
            boundary,
            List.of("message-1", "message-2"));

    assertThat(results).hasSize(2);
    assertThat(results.get(0).successful()).isTrue();
    assertThat(results.get(1).successful()).isFalse();
    assertThat(results.get(1).retryable()).isTrue();
    assertThat(results.get(1).reason()).isEqualTo("missingBatchPart");
  }

  @Test
  void batchParserMarksRateLimitedPartRetryable() {
    GmailOAuthProperties properties = new GmailOAuthProperties();
    GmailApiClient client = new GmailApiClient(properties);

    String boundary = "batch_response";
    String response =
        "--" + boundary + "\r\n"
            + "Content-Type: application/http\r\n"
            + "Content-ID: <response-gmail-0>\r\n\r\n"
            + "HTTP/1.1 403 Forbidden\r\n"
            + "Content-Type: application/json\r\n\r\n"
            + "{\"error\":{\"errors\":[{\"reason\":\"rateLimitExceeded\"}]}}\r\n"
            + "--" + boundary + "--\r\n";

    var results =
        client.parseBatchResponse(
            response.getBytes(java.nio.charset.StandardCharsets.UTF_8),
            boundary,
            List.of("message-1"));

    assertThat(results).hasSize(1);
    assertThat(results.getFirst().successful()).isFalse();
    assertThat(results.getFirst().retryable()).isTrue();
    assertThat(results.getFirst().reason()).isEqualTo("rateLimitExceeded");
  }

  @Test
  void batchMessagesRejectsMoreThanFiftyMessagesBeforeCallingGoogle() {
    GmailOAuthProperties properties = new GmailOAuthProperties();
    properties.setSyncBatchSize(100);
    GmailApiClient client = new GmailApiClient(properties);

    List<String> ids = IntStream.range(0, 51).mapToObj(index -> "message-" + index).toList();

    assertThatThrownBy(() -> client.batchMessages("token", ids))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("50");
  }
}
