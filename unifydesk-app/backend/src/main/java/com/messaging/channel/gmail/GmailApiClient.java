package com.messaging.channel.gmail;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.Base64Variants;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.ServiceUnavailableException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Component
public class GmailApiClient {

  static final int MAX_BATCH_SIZE = 50;
  private static final String BATCH_ENDPOINT = "/batch/gmail/v1";

  private static final Pattern GOOGLE_REASON_PATTERN =
      Pattern.compile("\\\"reason\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
  private static final Pattern GOOGLE_MESSAGE_PATTERN =
      Pattern.compile("\\\"message\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");

  private final RestClient googleOAuth = RestClient.create("https://oauth2.googleapis.com");
  private final RestClient gmailApi = RestClient.create("https://gmail.googleapis.com");
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final GmailOAuthProperties properties;

  public GmailApiClient(GmailOAuthProperties properties) {
    this.properties = properties;
  }

  public String authorizationUrl(String state) {
    requireConfigured();

    Map<String, String> params = new LinkedHashMap<>();
    params.put("client_id", properties.getClientId());
    params.put("redirect_uri", properties.getRedirectUri());
    params.put("response_type", "code");
    params.put("scope", properties.getScope());
    params.put("access_type", "offline");
    params.put("include_granted_scopes", "true");
    params.put("prompt", "consent");
    params.put("state", state);

    UriComponentsBuilder builder =
        UriComponentsBuilder.fromUriString("https://accounts.google.com/o/oauth2/v2/auth");
    params.forEach(builder::queryParam);
    return builder.build().encode().toUriString();
  }

  public TokenResponse exchangeCode(String code) {
    requireConfigured();

    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("code", code);
    form.add("client_id", properties.getClientId());
    form.add("client_secret", properties.getClientSecret());
    form.add("redirect_uri", properties.getRedirectUri());
    form.add("grant_type", "authorization_code");

    return executeOAuth(
        "token_exchange",
        () ->
            googleOAuth
                .post()
                .uri("/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenResponse.class));
  }

  public TokenResponse refreshAccessToken(String refreshToken) {
    requireConfigured();

    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("refresh_token", refreshToken);
    form.add("client_id", properties.getClientId());
    form.add("client_secret", properties.getClientSecret());
    form.add("grant_type", "refresh_token");

    return executeOAuth(
        "token_refresh",
        () ->
            googleOAuth
                .post()
                .uri("/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenResponse.class));
  }

  public GmailProfile profile(String accessToken) {
    return execute(
        "gmail_profile",
        () ->
            gmailApi
                .get()
                .uri("/gmail/v1/users/me/profile")
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .retrieve()
                .body(GmailProfile.class));
  }

  public MessageListResponse listMessages(
      String accessToken, int maxResults, String query, String pageToken) {
    URI uri = messagesListUri(maxResults, query, pageToken);
    return execute(
        "gmail_messages_list",
        () ->
            gmailApi
                .get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .retrieve()
                .body(MessageListResponse.class));
  }

  URI messagesListUri(int maxResults, String query, String pageToken) {
    UriComponentsBuilder builder =
        UriComponentsBuilder.fromPath("/gmail/v1/users/me/messages")
            .queryParam("maxResults", maxResults);

    if (query != null && !query.isBlank()) {
      builder.queryParam("q", query);
    }
    if (pageToken != null && !pageToken.isBlank()) {
      builder.queryParam("pageToken", pageToken);
    }

    return builder.build().encode().toUri();
  }

  public List<BatchMessageResult> batchMessages(String accessToken, List<String> messageIds) {
    if (messageIds == null || messageIds.isEmpty()) {
      return List.of();
    }
    // Existing durable sync jobs may contain batches created under an older configured
    // size. Accept them up to Gmail's hard HTTP batch limit; new jobs are created using
    // the current configured size in GmailSyncService.
    if (messageIds.size() > MAX_BATCH_SIZE) {
      throw new BadRequestException(
          "Gmail batch exceeds provider maximum of " + MAX_BATCH_SIZE);
    }

    String boundary = "messaging_" + UUID.randomUUID().toString().replace("-", "");
    String requestBody = batchRequestBody(boundary, messageIds);

    ResponseEntity<byte[]> response =
        execute(
            "gmail_messages_batch_get",
            () ->
                gmailApi
                    .post()
                    .uri(BATCH_ENDPOINT)
                    .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                    .header(HttpHeaders.CONTENT_TYPE, "multipart/mixed; boundary=" + boundary)
                    .body(requestBody)
                    .retrieve()
                    .toEntity(byte[].class));

    byte[] body = response.getBody();
    if (body == null || body.length == 0) {
      throw new BadRequestException("Google returned an empty Gmail batch response");
    }

    String contentType = response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
    String responseBoundary = multipartBoundary(contentType);
    return parseBatchResponse(body, responseBoundary, messageIds);
  }

  String batchRequestBody(String boundary, List<String> messageIds) {
    StringBuilder body = new StringBuilder(messageIds.size() * 180);
    for (int index = 0; index < messageIds.size(); index++) {
      String messageId = messageIds.get(index);
      body.append("--").append(boundary).append("\r\n");
      body.append("Content-Type: application/http\r\n");
      body.append("Content-ID: <gmail-").append(index).append(">\r\n\r\n");
      body.append("GET /gmail/v1/users/me/messages/")
          .append(messageId)
          .append("?format=full HTTP/1.1\r\n\r\n");
    }
    body.append("--").append(boundary).append("--\r\n");
    return body.toString();
  }

  private String multipartBoundary(String contentType) {
    if (contentType == null || contentType.isBlank()) {
      throw new BadRequestException("Google Gmail batch response is missing Content-Type");
    }
    Matcher matcher =
        Pattern.compile("boundary=\\\"?([^\\\";]+)\\\"?", Pattern.CASE_INSENSITIVE)
            .matcher(contentType);
    if (!matcher.find()) {
      throw new BadRequestException("Google Gmail batch response boundary is missing");
    }
    return matcher.group(1);
  }

  List<BatchMessageResult> parseBatchResponse(
      byte[] responseBody, String boundary, List<String> messageIds) {
    String payload = new String(responseBody, StandardCharsets.UTF_8);
    String[] parts = payload.split(Pattern.quote("--" + boundary));
    List<BatchMessageResult> results = new ArrayList<>(messageIds.size());
    int fallbackIndex = 0;

    for (String rawPart : parts) {
      String part = rawPart.trim();
      if (part.isEmpty() || "--".equals(part)) {
        continue;
      }

      int mimeEnd = part.indexOf("\r\n\r\n");
      if (mimeEnd < 0) {
        mimeEnd = part.indexOf("\n\n");
      }
      if (mimeEnd < 0) {
        continue;
      }

      String mimeHeaders = part.substring(0, mimeEnd);
      String httpPayload = part.substring(mimeEnd + (part.startsWith("\r\n", mimeEnd) ? 4 : 2));
      int index = batchResponseIndex(mimeHeaders, fallbackIndex++);
      if (index < 0 || index >= messageIds.size()) {
        continue;
      }

      results.add(parseBatchHttpPart(messageIds.get(index), httpPayload));
    }

    Map<String, BatchMessageResult> byMessageId = new LinkedHashMap<>();
    for (BatchMessageResult result : results) {
      byMessageId.put(result.messageId(), result);
    }

    List<BatchMessageResult> completed = new ArrayList<>(messageIds.size());
    for (String messageId : messageIds) {
      completed.add(
          byMessageId.getOrDefault(
              messageId,
              new BatchMessageResult(
                  messageId, null, 503, "missingBatchPart", true, Duration.ZERO)));
    }
    return completed;
  }

  private int batchResponseIndex(String headers, int fallback) {
    Matcher matcher =
        Pattern.compile("Content-ID:\\s*<response-gmail-(\\d+)>", Pattern.CASE_INSENSITIVE)
            .matcher(headers);
    return matcher.find() ? Integer.parseInt(matcher.group(1)) : fallback;
  }

  private BatchMessageResult parseBatchHttpPart(String messageId, String httpPayload) {
    String normalized = httpPayload.startsWith("\r\n") ? httpPayload.substring(2) : httpPayload;
    int firstLineEnd = normalized.indexOf("\r\n");
    if (firstLineEnd < 0) {
      firstLineEnd = normalized.indexOf('\n');
    }
    if (firstLineEnd < 0) {
      return new BatchMessageResult(
          messageId, null, 500, "malformedResponse", true, Duration.ZERO);
    }

    String statusLine = normalized.substring(0, firstLineEnd).trim();
    int status = httpStatus(statusLine);
    int headersEnd = normalized.indexOf("\r\n\r\n", firstLineEnd);
    int separatorLength = 4;
    if (headersEnd < 0) {
      headersEnd = normalized.indexOf("\n\n", firstLineEnd);
      separatorLength = 2;
    }
    String responseHeaders =
        headersEnd < 0 ? "" : normalized.substring(firstLineEnd, headersEnd);
    String json =
        headersEnd < 0 ? "" : normalized.substring(headersEnd + separatorLength).trim();
    Duration retryAfter = retryAfterFromRawHeaders(responseHeaders);

    if (status >= 200 && status < 300) {
      try {
        GmailMessage message = objectMapper.readValue(json, GmailMessage.class);
        return new BatchMessageResult(
            messageId, message, status, null, false, Duration.ZERO);
      } catch (JsonProcessingException exception) {
        return new BatchMessageResult(
            messageId, null, 500, "invalidJson", true, Duration.ZERO);
      }
    }

    String reason = extract(json, GOOGLE_REASON_PATTERN, "unknown");
    return new BatchMessageResult(
        messageId, null, status, reason, retryable(status, reason), retryAfter);
  }

  private int httpStatus(String statusLine) {
    Matcher matcher = Pattern.compile("HTTP/\\S+\\s+(\\d{3})").matcher(statusLine);
    return matcher.find() ? Integer.parseInt(matcher.group(1)) : 500;
  }

  public SendMessageResponse sendMessage(String accessToken, String rawMessage) {
    return sendMessage(accessToken, rawMessage, null);
  }

  public SendMessageResponse sendMessage(
      String accessToken, String rawMessage, String threadId) {
    return execute(
        "gmail_message_send",
        () ->
            gmailApi
                .post()
                .uri("/gmail/v1/users/me/messages/send")
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new SendMessageRequest(rawMessage, threadId))
                .retrieve()
                .body(SendMessageResponse.class));
  }

  /**
   * Single-attempt send for non-idempotent bulk delivery. The caller owns retry classification so
   * an ambiguous provider response is never retried automatically.
   */
  public SendMessageResponse sendMessageOnce(
      String accessToken, String rawMessage, String threadId) {
    try {
      SendMessageResponse response =
          gmailApi
              .post()
              .uri("/gmail/v1/users/me/messages/send")
              .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
              .contentType(MediaType.APPLICATION_JSON)
              .body(new SendMessageRequest(rawMessage, threadId))
              .retrieve()
              .body(SendMessageResponse.class);
      if (response == null) {
        throw new ServiceUnavailableException("Google returned an empty Gmail send response");
      }
      return response;
    } catch (RestClientResponseException exception) {
      int status = exception.getStatusCode().value();
      GoogleFailure failure = googleFailure(exception);
      if (isRateLimit(status, failure.reason())) {
        throw new GmailRateLimitException(
            failure.reason(), retryAfter(exception.getResponseHeaders()));
      }
      if (status >= 500) {
        throw new ServiceUnavailableException(
            "Gmail send outcome could not be confirmed", exception);
      }
      if (status == 403) {
        throw new BadRequestException(googleForbiddenMessage(failure.reason()));
      }
      throw new BadRequestException("Google Gmail send failed with HTTP " + status);
    }
  }

  public void updateReadState(
      String accessToken, List<String> messageIds, boolean read) {
    if (messageIds == null || messageIds.isEmpty()) return;
    if (messageIds.size() > 1000) {
      throw new BadRequestException("Gmail read-state batch cannot exceed 1000 messages");
    }

    BatchModifyRequest request =
        read
            ? new BatchModifyRequest(messageIds, List.of(), List.of("UNREAD"))
            : new BatchModifyRequest(messageIds, List.of("UNREAD"), List.of());

    execute(
        "gmail_messages_batch_modify",
        () -> {
          gmailApi
              .post()
              .uri("/gmail/v1/users/me/messages/batchModify")
              .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
              .contentType(MediaType.APPLICATION_JSON)
              .body(request)
              .retrieve()
              .toBodilessEntity();
          return Boolean.TRUE;
        });
  }

  public void streamAttachment(
      String accessToken, String messageId, String attachmentId, OutputStream outputStream) {
    if (attachmentId == null || attachmentId.isBlank()) {
      throw new BadRequestException("Gmail attachment id is required");
    }

    URI uri =
        UriComponentsBuilder.fromPath(
                "/gmail/v1/users/me/messages/{messageId}/attachments/{attachmentId}")
            .buildAndExpand(messageId, attachmentId)
            .encode()
            .toUri();

    try {
      gmailApi
          .get()
          .uri(uri)
          .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
          .exchange(
              (request, response) -> {
                if (!response.getStatusCode().is2xxSuccessful()) {
                  throw new ServiceUnavailableException(
                      "Google Gmail attachment download failed with HTTP "
                          + response.getStatusCode().value());
                }
                try (JsonParser parser = objectMapper.getFactory().createParser(response.getBody())) {
                  while (parser.nextToken() != null) {
                    if (parser.currentToken() == JsonToken.FIELD_NAME
                        && "data".equals(parser.currentName())) {
                      if (parser.nextToken() != JsonToken.VALUE_STRING) {
                        throw new ServiceUnavailableException(
                            "Google returned an invalid Gmail attachment payload");
                      }
                      parser.readBinaryValue(Base64Variants.MODIFIED_FOR_URL, outputStream);
                      outputStream.flush();
                      return null;
                    }
                  }
                  throw new ServiceUnavailableException(
                      "Google returned Gmail attachment metadata without content");
                } catch (IOException exception) {
                  throw new ServiceUnavailableException(
                      "Unable to stream Gmail attachment content", exception);
                }
              });
    } catch (RestClientResponseException exception) {
      int status = exception.getStatusCode().value();
      if (status == 404) {
        throw new BadRequestException("Gmail attachment is no longer available");
      }
      if (status == 401 || status == 403) {
        throw new BadRequestException(
            "Gmail mailbox authorization is invalid or missing required permissions");
      }
      throw new ServiceUnavailableException(
          "Unable to download Gmail attachment", exception);
    }
  }

  public GmailMessage message(String accessToken, String messageId) {
    URI uri =
        UriComponentsBuilder.fromPath("/gmail/v1/users/me/messages/{messageId}")
            .queryParam("format", "full")
            .buildAndExpand(messageId)
            .encode()
            .toUri();

    return execute(
        "gmail_message_get",
        () ->
            gmailApi
                .get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .retrieve()
                .body(GmailMessage.class));
  }

  public WatchResponse watch(String accessToken, String topicName) {
    if (topicName == null || topicName.isBlank()) {
      throw new BadRequestException("Gmail Pub/Sub topic is not configured");
    }
    return execute(
        "gmail_watch",
        () ->
            gmailApi
                .post()
                .uri("/gmail/v1/users/me/watch")
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new WatchRequest(topicName))
                .retrieve()
                .body(WatchResponse.class));
  }

  public void stopWatch(String accessToken) {
    execute(
        "gmail_stop_watch",
        () ->
            gmailApi
                .post()
                .uri("/gmail/v1/users/me/stop")
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .retrieve()
                .toBodilessEntity());
  }

  public HistoryListResponse history(
      String accessToken, String startHistoryId, String pageToken, int maxResults) {
    if (startHistoryId == null || startHistoryId.isBlank()) {
      throw new BadRequestException("Gmail startHistoryId is required");
    }

    UriComponentsBuilder builder =
        UriComponentsBuilder.fromPath("/gmail/v1/users/me/history")
            .queryParam("startHistoryId", startHistoryId)
            .queryParam("maxResults", Math.max(1, Math.min(maxResults, 500)))
            .queryParam("historyTypes", "messageAdded")
            .queryParam("historyTypes", "messageDeleted")
            .queryParam("historyTypes", "labelAdded")
            .queryParam("historyTypes", "labelRemoved");

    if (pageToken != null && !pageToken.isBlank()) {
      builder.queryParam("pageToken", pageToken);
    }

    URI uri = builder.build().encode().toUri();
    return execute(
        "gmail_history_list",
        () ->
            gmailApi
                .get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .retrieve()
                .body(HistoryListResponse.class));
  }

  public void revokeBestEffort(String token) {
    if (token == null || token.isBlank()) return;

    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("token", token);

    try {
      googleOAuth
          .post()
          .uri("/revoke")
          .contentType(MediaType.APPLICATION_FORM_URLENCODED)
          .body(form)
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientResponseException exception) {
      log.warn("Google token revoke failed status={}", exception.getStatusCode().value());
      // Local disconnect still proceeds when Google already revoked or expired the grant.
    }
  }

  private void requireConfigured() {
    if (!properties.configured()) {
      throw new BadRequestException(
          "Gmail is not configured. Set the Google OAuth environment variables first");
    }
  }

  private String bearer(String accessToken) {
    return "Bearer " + accessToken;
  }

  private <T> T execute(String operation, GoogleCall<T> call) {
    return execute(operation, call, true);
  }

  private <T> T executeOAuth(String operation, GoogleCall<T> call) {
    return execute(operation, call, false);
  }

  private <T> T execute(String operation, GoogleCall<T> call, boolean gmailQuotaAware) {
    int maxAttempts = Math.max(1, properties.getApiRetryMaxAttempts());

    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      try {
        T result = call.execute();
        if (result == null) {
          throw new BadRequestException("Google returned an empty response");
        }
        return result;
      } catch (RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        GoogleFailure failure = googleFailure(exception);

        if (gmailQuotaAware && isRateLimit(status, failure.reason())) {
          Duration retryAfter = retryAfter(exception.getResponseHeaders());
          String throttleReason =
              status == 429
                      && (failure.reason() == null
                          || failure.reason().isBlank()
                          || "unknown".equalsIgnoreCase(failure.reason()))
                  ? "rateLimitExceeded"
                  : failure.reason();
          log.warn(
              "Google Gmail quota response operation={} status={} reason={} retryAfterMs={}",
              operation,
              status,
              throttleReason,
              retryAfter.toMillis());
          throw new GmailRateLimitException(throttleReason, retryAfter);
        }

        boolean transientFailure = status == 429 || status >= 500;
        if (transientFailure && attempt < maxAttempts) {
          long delayMs = retryDelayMillis(attempt);
          log.warn(
              "Google API request retrying operation={} status={} reason={} attempt={}/{} delayMs={}",
              operation,
              status,
              failure.reason(),
              attempt,
              maxAttempts,
              delayMs);
          sleep(delayMs);
          continue;
        }

        log.warn(
            "Google API request failed operation={} status={} reason={} attempts={}",
            operation,
            status,
            failure.reason(),
            attempt);

        if (transientFailure) {
          throw new ServiceUnavailableException(
              gmailQuotaAware
                  ? "Gmail is temporarily unavailable. The request will be retried automatically."
                  : "Google OAuth is temporarily unavailable. Please try again.");
        }

        if ("gmail_history_list".equals(operation) && status == 404) {
          throw new GmailHistoryExpiredException();
        }

        if (status == 403) {
          throw new BadRequestException(googleForbiddenMessage(failure.reason()));
        }

        throw new BadRequestException("Google API request failed with HTTP " + status);
      }
    }

    throw new ServiceUnavailableException("Gmail is temporarily unavailable");
  }

  private boolean retryable(int status, String reason) {
    return isRateLimit(status, reason) || status >= 500;
  }

  private boolean isRateLimit(int status, String reason) {
    return status == 429
        || "rateLimitExceeded".equals(reason)
        || "userRateLimitExceeded".equals(reason);
  }

  private long retryDelayMillis(int attempt) {
    long initial = Math.max(100, properties.getApiRetryInitialBackoff().toMillis());
    long maximum = Math.max(initial, properties.getApiRetryMaxBackoff().toMillis());
    long exponential = initial * (1L << Math.min(attempt - 1, 20));
    long jitter = ThreadLocalRandom.current().nextLong(0, 501);
    return Math.min(maximum, exponential + jitter);
  }

  private void sleep(long delayMs) {
    try {
      Thread.sleep(delayMs);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new BadRequestException("Gmail sync was interrupted");
    }
  }

  private Duration retryAfterFromRawHeaders(String rawHeaders) {
    if (rawHeaders == null || rawHeaders.isBlank()) {
      return Duration.ZERO;
    }
    Matcher matcher =
        Pattern.compile("(?im)^Retry-After:\\s*([^\\r\\n]+)").matcher(rawHeaders);
    return matcher.find() ? retryAfter(matcher.group(1)) : Duration.ZERO;
  }

  private Duration retryAfter(HttpHeaders headers) {
    if (headers == null) {
      return Duration.ZERO;
    }
    return retryAfter(headers.getFirst(HttpHeaders.RETRY_AFTER));
  }

  private Duration retryAfter(String value) {
    if (value == null || value.isBlank()) {
      return Duration.ZERO;
    }
    String trimmed = value.trim();
    try {
      long seconds = Long.parseLong(trimmed);
      return Duration.ofSeconds(Math.max(0L, seconds));
    } catch (NumberFormatException ignored) {
      try {
        ZonedDateTime retryAt =
            ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME);
        Duration delay = Duration.between(ZonedDateTime.now(retryAt.getZone()), retryAt);
        return delay.isNegative() ? Duration.ZERO : delay;
      } catch (RuntimeException ignoredDate) {
        return Duration.ZERO;
      }
    }
  }

  private GoogleFailure googleFailure(RestClientResponseException exception) {
    String responseBody = exception.getResponseBodyAsString();
    return new GoogleFailure(
        extract(responseBody, GOOGLE_REASON_PATTERN, "unknown"),
        extract(responseBody, GOOGLE_MESSAGE_PATTERN, "Google API request failed"));
  }

  private String extract(String value, Pattern pattern, String fallback) {
    if (value == null || value.isBlank()) {
      return fallback;
    }
    Matcher matcher = pattern.matcher(value);
    return matcher.find() ? matcher.group(1) : fallback;
  }

  private String googleForbiddenMessage(String reason) {
    return switch (reason) {
      case "insufficientPermissions" ->
          "Gmail permission is insufficient. Disconnect and reconnect the Gmail account.";
      case "domainPolicy" -> "Google Workspace policy blocks Gmail API access for this account.";
      default -> "Google denied Gmail API access (" + reason + ").";
    };
  }

  private record GoogleFailure(String reason, String message) {}

  @FunctionalInterface
  private interface GoogleCall<T> {
    T execute();
  }

  public record TokenResponse(
      @JsonProperty("access_token") String accessToken,
      @JsonProperty("refresh_token") String refreshToken,
      @JsonProperty("expires_in") Long expiresIn,
      @JsonProperty("token_type") String tokenType,
      String scope) {}

  public record GmailProfile(
      @JsonProperty("emailAddress") String emailAddress,
      @JsonProperty("messagesTotal") Long messagesTotal,
      @JsonProperty("threadsTotal") Long threadsTotal,
      @JsonProperty("historyId") String historyId) {}

  public record MessageListResponse(
      List<MessageReference> messages,
      @JsonProperty("nextPageToken") String nextPageToken,
      @JsonProperty("resultSizeEstimate") Integer resultSizeEstimate) {}

  public record MessageReference(String id, String threadId) {}

  public record BatchMessageResult(
      String messageId,
      GmailMessage message,
      int status,
      String reason,
      boolean retryable,
      Duration retryAfter) {

    public boolean successful() {
      return message != null && status >= 200 && status < 300;
    }
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record SendMessageRequest(String raw, String threadId) {}

  public record BatchModifyRequest(
      List<String> ids, List<String> addLabelIds, List<String> removeLabelIds) {}

  public record WatchRequest(String topicName) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record WatchResponse(String historyId, Long expiration) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record HistoryListResponse(
      List<HistoryRecord> history,
      String nextPageToken,
      String historyId) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record HistoryRecord(
      String id,
      List<HistoryMessageChange> messagesAdded,
      List<HistoryMessageChange> messagesDeleted,
      List<HistoryLabelChange> labelsAdded,
      List<HistoryLabelChange> labelsRemoved) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record HistoryMessageChange(HistoryMessage message) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record HistoryLabelChange(HistoryMessage message, List<String> labelIds) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record HistoryMessage(String id, String threadId, List<String> labelIds) {}

  public record SendMessageResponse(String id, String threadId, List<String> labelIds) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record GmailMessage(
      String id,
      String threadId,
      List<String> labelIds,
      String snippet,
      String historyId,
      String internalDate,
      GmailPayload payload) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record GmailPayload(
      String mimeType,
      String filename,
      List<GmailHeader> headers,
      GmailBody body,
      List<GmailPayload> parts) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record GmailHeader(String name, String value) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record GmailBody(
      @JsonProperty("attachmentId") String attachmentId, Integer size, String data) {}
}
