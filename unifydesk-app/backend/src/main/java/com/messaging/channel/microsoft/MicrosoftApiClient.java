package com.messaging.channel.microsoft;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.messaging.channel.email.EmailFolder;
import com.messaging.channel.email.EmailSyncMode;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.ServiceUnavailableException;
import com.messaging.common.exception.TooManyRequestsException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

@Slf4j
@Component
public class MicrosoftApiClient {

  private static final String GRAPH_BASE = "https://graph.microsoft.com";
  private static final String MESSAGE_SELECT =
      "id,conversationId,subject,bodyPreview,body,from,replyTo,toRecipients,ccRecipients,"
          + "bccRecipients,receivedDateTime,sentDateTime,isRead,hasAttachments,internetMessageId,"
          + "internetMessageHeaders";

  private final RestClient identity = RestClient.create("https://login.microsoftonline.com");
  private final RestClient graph = RestClient.create(GRAPH_BASE);
  private final MicrosoftOAuthProperties properties;

  public MicrosoftApiClient(MicrosoftOAuthProperties properties) {
    this.properties = properties;
  }

  public String authorizationUrl(String state) {
    requireConfigured();

    Map<String, String> params = new LinkedHashMap<>();
    params.put("client_id", properties.getClientId());
    params.put("response_type", "code");
    params.put("redirect_uri", properties.getRedirectUri());
    params.put("response_mode", "query");
    params.put("scope", properties.getScope());
    params.put("state", state);
    params.put("prompt", "select_account");

    UriComponentsBuilder builder =
        UriComponentsBuilder.fromUriString(
            "https://login.microsoftonline.com/"
                + properties.getTenant()
                + "/oauth2/v2.0/authorize");
    params.forEach(builder::queryParam);
    return builder.build().encode().toUriString();
  }

  public TokenResponse exchangeCode(String code) {
    MultiValueMap<String, String> form = tokenForm();
    form.add("code", code);
    form.add("redirect_uri", properties.getRedirectUri());
    form.add("grant_type", "authorization_code");
    return execute(
        "token_exchange",
        () ->
            identity
                .post()
                .uri("/" + properties.getTenant() + "/oauth2/v2.0/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenResponse.class));
  }

  public TokenResponse refreshAccessToken(String refreshToken) {
    MultiValueMap<String, String> form = tokenForm();
    form.add("refresh_token", refreshToken);
    form.add("grant_type", "refresh_token");
    return execute(
        "token_refresh",
        () ->
            identity
                .post()
                .uri("/" + properties.getTenant() + "/oauth2/v2.0/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenResponse.class));
  }

  private MultiValueMap<String, String> tokenForm() {
    requireConfigured();
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("client_id", properties.getClientId());
    form.add("client_secret", properties.getClientSecret());
    form.add("scope", properties.getScope());
    return form;
  }

  public MicrosoftProfile profile(String accessToken) {
    return execute(
        "profile",
        () ->
            graph
                .get()
                .uri("/v1.0/me?$select=id,displayName,mail,userPrincipalName")
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .retrieve()
                .body(MicrosoftProfile.class));
  }

  public DeltaResponse delta(String accessToken, String folderKey, String continuationUrl) {
    URI uri =
        hasText(continuationUrl) ? trustedGraphContinuationUri(continuationUrl) : initialDeltaUri(folderKey);

    return execute(
        "mail_delta",
        () ->
            graph
                .get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .header(
                    "Prefer",
                    "IdType=\"ImmutableId\", odata.maxpagesize="
                        + Math.max(1, Math.min(properties.getDeltaPageSize(), 200)))
                .retrieve()
                .body(DeltaResponse.class));
  }

  URI trustedGraphContinuationUri(String continuationUrl) {
    URI uri;
    try {
      uri = URI.create(continuationUrl);
    } catch (IllegalArgumentException exception) {
      throw new BadRequestException("Microsoft Graph returned an invalid delta continuation URL");
    }

    boolean trusted =
        "https".equalsIgnoreCase(uri.getScheme())
            && "graph.microsoft.com".equalsIgnoreCase(uri.getHost());
    if (!trusted) {
      throw new BadRequestException("Microsoft Graph returned an untrusted delta continuation URL");
    }
    return uri;
  }

  URI initialDeltaUri(String folderKey) {
    Instant initialFrom = Instant.now().minus(properties.getLiveInitialLookback());
    return UriComponentsBuilder.fromUriString(
            GRAPH_BASE + "/v1.0/me/mailFolders/{folder}/messages/delta")
        .queryParam("$select", MESSAGE_SELECT)
        .queryParam("$filter", "receivedDateTime ge " + initialFrom)
        .queryParam(
            "$expand",
            "attachments($select=id,name,contentType,size,isInline)")
        .queryParam("$top", Math.max(1, Math.min(properties.getDeltaPageSize(), 200)))
        .buildAndExpand(folderKey)
        .encode()
        .toUri();
  }

  public MessagePage listMessages(
      String accessToken,
      EmailFolder folder,
      EmailSyncMode mode,
      LocalDate fromDate,
      LocalDate toDate,
      int maxResults,
      String continuationUrl) {
    URI uri =
        hasText(continuationUrl)
            ? trustedGraphContinuationUri(continuationUrl)
            : historyListUri(folder, mode, fromDate, toDate, maxResults);

    return execute(
        "mail_history_list",
        () ->
            graph
                .get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .header("Prefer", "IdType=\"ImmutableId\"")
                .retrieve()
                .body(MessagePage.class));
  }

  URI historyListUri(
      EmailFolder folder,
      EmailSyncMode mode,
      LocalDate fromDate,
      LocalDate toDate,
      int maxResults) {
    int pageSize = Math.max(1, Math.min(maxResults, 200));
    String timeField = folder == EmailFolder.SENT ? "sentDateTime" : "receivedDateTime";

    UriComponentsBuilder builder =
        UriComponentsBuilder.fromUriString(
                GRAPH_BASE + "/v1.0/me/mailFolders/{folder}/messages")
            .queryParam("$select", MESSAGE_SELECT)
            .queryParam(
                "$expand",
                "attachments($select=id,name,contentType,size,isInline)")
            .queryParam("$orderby", timeField + " desc")
            .queryParam("$top", pageSize);

    if (mode == EmailSyncMode.DATE_RANGE) {
      Instant from = fromDate.atStartOfDay().toInstant(ZoneOffset.UTC);
      Instant until = toDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
      builder.queryParam(
          "$filter",
          timeField + " ge " + from + " and " + timeField + " lt " + until);
    }

    return builder
        .buildAndExpand(folder.microsoftFolderKey())
        .encode()
        .toUri();
  }

  public void streamAttachment(
      String accessToken, String messageId, String attachmentId, OutputStream outputStream) {
    if (!hasText(messageId) || !hasText(attachmentId)) {
      throw new BadRequestException("Microsoft attachment identifiers are required");
    }

    try {
      graph
          .get()
          .uri(
              "/v1.0/me/messages/{messageId}/attachments/{attachmentId}/$value",
              messageId,
              attachmentId)
          .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
          .header("Prefer", "IdType=\\\"ImmutableId\\\"")
          .exchange(
              (request, response) -> {
                int status = response.getStatusCode().value();
                if (status == 404) {
                  throw new GraphResourceNotFoundException();
                }
                if (!response.getStatusCode().is2xxSuccessful()) {
                  throw new ServiceUnavailableException(
                      "Microsoft attachment download failed with HTTP " + status);
                }
                try {
                  response.getBody().transferTo(outputStream);
                  outputStream.flush();
                  return null;
                } catch (IOException exception) {
                  throw new ServiceUnavailableException(
                      "Unable to stream Microsoft attachment content", exception);
                }
              });
    } catch (GraphResourceNotFoundException exception) {
      throw new BadRequestException("Microsoft attachment is no longer available");
    } catch (RestClientResponseException exception) {
      int status = exception.getStatusCode().value();
      if (status == 404) {
        throw new BadRequestException("Microsoft attachment is no longer available");
      }
      if (status == 401 || status == 403) {
        throw new BadRequestException(
            "Microsoft mailbox authorization is invalid or missing required permissions");
      }
      throw new ServiceUnavailableException(
          "Unable to download Microsoft attachment", exception);
    }
  }

  public void updateReadState(
      String accessToken, List<String> messageIds, boolean read) {
    if (messageIds == null || messageIds.isEmpty()) return;

    for (int offset = 0; offset < messageIds.size(); offset += 20) {
      int batchOffset = offset;
      int end = Math.min(messageIds.size(), batchOffset + 20);
      List<GraphBatchSubRequest> requests =
          java.util.stream.IntStream.range(batchOffset, end)
              .mapToObj(
                  index -> {
                    String messageId = messageIds.get(index);
                    String encodedId =
                        UriUtils.encodePathSegment(messageId, StandardCharsets.UTF_8);
                    return new GraphBatchSubRequest(
                        Integer.toString(index - batchOffset + 1),
                        "PATCH",
                        "/me/messages/" + encodedId,
                        Map.of(
                            "Content-Type", "application/json",
                            "Prefer", "IdType=\"ImmutableId\""),
                        Map.of("isRead", read));
                  })
              .toList();

      GraphBatchResponse response =
          execute(
              "mail_batch_read_state",
              () ->
                  graph
                      .post()
                      .uri("/v1.0/$batch")
                      .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                      .contentType(MediaType.APPLICATION_JSON)
                      .body(new GraphBatchRequest(requests))
                      .retrieve()
                      .body(GraphBatchResponse.class));

      if (response.responses() == null) {
        throw new BadRequestException("Microsoft Graph returned an invalid batch response");
      }
      for (GraphBatchSubResponse item : response.responses()) {
        int status = item == null || item.status() == null ? 500 : item.status();
        if ((status >= 200 && status < 300) || status == 404) continue;
        if (status == 429 || status >= 500) {
          throw new TooManyRequestsException(
              "Microsoft Graph is temporarily unavailable or rate limited");
        }
        if (status == 401 || status == 403) {
          throw new BadRequestException(
              "Microsoft mailbox authorization is invalid or missing required permissions");
        }
        throw new BadRequestException(
            "Microsoft Graph read-state update failed with HTTP " + status);
      }
    }
  }

  public GraphMessage createDraft(String accessToken, GraphSendMessage message) {
    return execute(
        "draft_create",
        () ->
            graph
                .post()
                .uri("/v1.0/me/messages")
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .header("Prefer", "IdType=\"ImmutableId\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(message)
                .retrieve()
                .body(GraphMessage.class));
  }

  public GraphMessage createReplyDraft(
      String accessToken, String messageId, boolean replyAll) {
    String action = replyAll ? "createReplyAll" : "createReply";
    return execute(
        "reply_draft_create",
        () ->
            graph
                .post()
                .uri("/v1.0/me/messages/{id}/" + action, messageId)
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .header("Prefer", "IdType=\"ImmutableId\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of())
                .retrieve()
                .body(GraphMessage.class));
  }

  public void updateDraftBody(String accessToken, String draftId, String bodyText) {
    executeVoid(
        "draft_body_update",
        () ->
            graph
                .patch()
                .uri("/v1.0/me/messages/{id}", draftId)
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .header("Prefer", "IdType=\"ImmutableId\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("body", Map.of("contentType", "Text", "content", bodyText == null ? "" : bodyText)))
                .retrieve()
                .toBodilessEntity());
  }

  public void addSmallFileAttachment(
      String accessToken,
      String draftId,
      String name,
      String contentType,
      byte[] content) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("@odata.type", "#microsoft.graph.fileAttachment");
    payload.put("name", name);
    payload.put("contentType", contentType);
    payload.put("isInline", false);
    payload.put("contentBytes", Base64.getEncoder().encodeToString(content));

    executeVoid(
        "attachment_create",
        () ->
            graph
                .post()
                .uri("/v1.0/me/messages/{id}/attachments", draftId)
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .header("Prefer", "IdType=\"ImmutableId\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toBodilessEntity());
  }

  public AttachmentUploadSession createAttachmentUploadSession(
      String accessToken,
      String draftId,
      String name,
      String contentType,
      long sizeBytes) {
    Map<String, Object> item = new LinkedHashMap<>();
    item.put("attachmentType", "file");
    item.put("name", name);
    item.put("size", sizeBytes);
    item.put("contentType", contentType);
    item.put("isInline", false);

    return execute(
        "attachment_upload_session_create",
        () ->
            graph
                .post()
                .uri("/v1.0/me/messages/{id}/attachments/createUploadSession", draftId)
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .header("Prefer", "IdType=\"ImmutableId\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("AttachmentItem", item))
                .retrieve()
                .body(AttachmentUploadSession.class));
  }

  public void uploadAttachmentChunk(
      String uploadUrl, byte[] content, long startInclusive, long totalBytes) {
    if (content == null || content.length == 0) {
      throw new BadRequestException("Microsoft attachment chunk is empty");
    }
    long endInclusive = startInclusive + content.length - 1;
    try {
      RestClient.create()
          .put()
          .uri(URI.create(uploadUrl))
          .contentType(MediaType.APPLICATION_OCTET_STREAM)
          .contentLength(content.length)
          .header(
              HttpHeaders.CONTENT_RANGE,
              "bytes " + startInclusive + "-" + endInclusive + "/" + totalBytes)
          .body(content)
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientResponseException exception) {
      int status = exception.getStatusCode().value();
      if (status == 429 || status >= 500) {
        throw new ServiceUnavailableException(
            "Microsoft attachment upload is temporarily unavailable", exception);
      }
      throw new BadRequestException(
          "Microsoft attachment upload failed with HTTP " + status);
    }
  }

  public void sendDraft(String accessToken, String draftId) {
    executeVoid(
        "draft_send",
        () ->
            graph
                .post()
                .uri("/v1.0/me/messages/{id}/send", draftId)
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .header("Prefer", "IdType=\"ImmutableId\"")
                .retrieve()
                .toBodilessEntity());
  }

  public void deleteDraftBestEffort(String accessToken, String draftId) {
    if (!hasText(draftId)) return;
    try {
      graph
          .delete()
          .uri("/v1.0/me/messages/{id}", draftId)
          .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
          .header("Prefer", "IdType=\"ImmutableId\"")
          .retrieve()
          .toBodilessEntity();
    } catch (RuntimeException exception) {
      log.warn("Microsoft draft cleanup failed draftId={}", draftId);
    }
  }

  public void sendMail(String accessToken, SendMailRequest request) {
    executeVoid(
        "send_mail",
        () ->
            graph
                .post()
                .uri("/v1.0/me/sendMail")
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity());
  }

  /**
   * Single-attempt send for non-idempotent bulk delivery. No automatic 5xx/429 retry occurs here.
   */
  public void sendMailOnce(String accessToken, SendMailRequest request) {
    try {
      graph
          .post()
          .uri("/v1.0/me/sendMail")
          .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
          .contentType(MediaType.APPLICATION_JSON)
          .body(request)
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientResponseException exception) {
      int status = exception.getStatusCode().value();
      if (status == 429) {
        throw new TooManyRequestsException("Microsoft Graph rate limited the send");
      }
      if (status >= 500) {
        throw new ServiceUnavailableException(
            "Microsoft send outcome could not be confirmed", exception);
      }
      throw new BadRequestException("Microsoft Graph send failed with HTTP " + status);
    }
  }


  public void reply(String accessToken, String messageId, String comment, boolean replyAll) {
    String action = replyAll ? "replyAll" : "reply";
    executeVoid(
        replyAll ? "reply_all" : "reply",
        () ->
            graph
                .post()
                .uri("/v1.0/me/messages/{id}/" + action, messageId)
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .header("Prefer", "IdType=\"ImmutableId\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("comment", comment == null ? "" : comment))
                .retrieve()
                .toBodilessEntity());
  }

  public SubscriptionResponse createSubscription(
      String accessToken, String notificationUrl, String clientState, Instant expiresAt) {
    SubscriptionRequest request =
        new SubscriptionRequest(
            "created,updated,deleted",
            notificationUrl,
            "me/messages",
            expiresAt.toString(),
            clientState);

    return execute(
        "subscription_create",
        () ->
            graph
                .post()
                .uri("/v1.0/subscriptions")
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .header("Prefer", "IdType=\"ImmutableId\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(SubscriptionResponse.class));
  }

  public SubscriptionResponse renewSubscription(
      String accessToken, String subscriptionId, Instant expiresAt) {
    return execute(
        "subscription_renew",
        () ->
            graph
                .patch()
                .uri("/v1.0/subscriptions/{id}", subscriptionId)
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("expirationDateTime", expiresAt.toString()))
                .retrieve()
                .body(SubscriptionResponse.class));
  }

  public void deleteSubscriptionBestEffort(String accessToken, String subscriptionId) {
    if (!hasText(subscriptionId)) return;
    try {
      graph
          .delete()
          .uri("/v1.0/subscriptions/{id}", subscriptionId)
          .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientResponseException exception) {
      log.warn(
          "Microsoft subscription delete failed subscriptionId={} status={}",
          subscriptionId,
          exception.getStatusCode().value());
    }
  }

  private <T> T execute(String operation, GraphCall<T> call) {
    int maxAttempts = Math.max(1, properties.getApiRetryMaxAttempts());
    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      try {
        T value = call.execute();
        if (value == null) {
          throw new BadRequestException("Microsoft Graph returned an empty response");
        }
        return value;
      } catch (RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        boolean retryable = status == 429 || status >= 500;
        if (retryable && attempt < maxAttempts) {
          long delayMs = retryDelayMillis(exception, attempt);
          log.warn(
              "Microsoft Graph retry operation={} status={} attempt={}/{} delayMs={}",
              operation,
              status,
              attempt,
              maxAttempts,
              delayMs);
          sleep(delayMs);
          continue;
        }
        if (retryable) {
          throw new TooManyRequestsException(
              "Microsoft Graph is temporarily unavailable or rate limited");
        }
        if (status == 404) {
          throw new GraphResourceNotFoundException();
        }
        if (status == 401 || status == 403) {
          throw new BadRequestException(
              "Microsoft mailbox authorization is invalid or missing required permissions");
        }
        if (status == 410) {
          throw new DeltaCursorExpiredException();
        }
        throw new BadRequestException("Microsoft Graph request failed with HTTP " + status);
      }
    }
    throw new BadRequestException("Microsoft Graph request failed");
  }

  private void executeVoid(String operation, GraphVoidCall call) {
    int maxAttempts = Math.max(1, properties.getApiRetryMaxAttempts());
    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      try {
        call.execute();
        return;
      } catch (RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        boolean retryable = status == 429 || status >= 500;
        if (retryable && attempt < maxAttempts) {
          sleep(retryDelayMillis(exception, attempt));
          continue;
        }
        if (retryable) {
          throw new TooManyRequestsException(
              "Microsoft Graph is temporarily unavailable or rate limited");
        }
        throw new BadRequestException("Microsoft Graph request failed with HTTP " + status);
      }
    }
  }

  private long retryDelayMillis(RestClientResponseException exception, int attempt) {
    String retryAfter = exception.getResponseHeaders() == null
        ? null
        : exception.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER);
    if (hasText(retryAfter)) {
      try {
        return Math.max(1000L, Long.parseLong(retryAfter.trim()) * 1000L);
      } catch (NumberFormatException ignored) {
        // Fall back to exponential backoff.
      }
    }

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
      throw new BadRequestException("Microsoft Graph request interrupted");
    }
  }

  private void requireConfigured() {
    if (!properties.configured()) {
      throw new BadRequestException(
          "Microsoft email is not configured. Set the Microsoft OAuth environment variables first");
    }
  }

  private String bearer(String token) {
    return "Bearer " + token;
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  public static final class DeltaCursorExpiredException extends RuntimeException {}

  public static final class GraphResourceNotFoundException extends RuntimeException {}

  @FunctionalInterface
  private interface GraphCall<T> {
    T execute();
  }

  @FunctionalInterface
  private interface GraphVoidCall {
    void execute();
  }

  public record TokenResponse(
      @JsonProperty("access_token") String accessToken,
      @JsonProperty("refresh_token") String refreshToken,
      @JsonProperty("expires_in") Long expiresIn,
      @JsonProperty("token_type") String tokenType,
      String scope) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record MicrosoftProfile(
      String id, String displayName, String mail, String userPrincipalName) {
    public String emailAddress() {
      return mail != null && !mail.isBlank() ? mail : userPrincipalName;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record MessagePage(
      List<GraphMessage> value,
      @JsonProperty("@odata.nextLink") String nextLink) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record DeltaResponse(
      List<GraphMessage> value,
      @JsonProperty("@odata.nextLink") String nextLink,
      @JsonProperty("@odata.deltaLink") String deltaLink) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record GraphMessage(
      String id,
      String conversationId,
      String subject,
      String bodyPreview,
      GraphBody body,
      GraphRecipient from,
      List<GraphRecipient> replyTo,
      List<GraphRecipient> toRecipients,
      List<GraphRecipient> ccRecipients,
      List<GraphRecipient> bccRecipients,
      String receivedDateTime,
      String sentDateTime,
      Boolean isRead,
      Boolean hasAttachments,
      String internetMessageId,
      List<GraphHeader> internetMessageHeaders,
      List<GraphAttachment> attachments,
      @JsonProperty("@removed") Map<String, Object> removed) {
    public boolean removedMessage() {
      return removed != null;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record GraphBody(String contentType, String content) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record GraphRecipient(GraphEmailAddress emailAddress) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record GraphEmailAddress(String name, String address) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record GraphHeader(String name, String value) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record GraphAttachment(
      String id, String name, String contentType, Long size, Boolean isInline) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record AttachmentUploadSession(
      String uploadUrl,
      String expirationDateTime,
      List<String> nextExpectedRanges) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record SendMailRequest(GraphSendMessage message, Boolean saveToSentItems) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record GraphSendMessage(
      String subject,
      GraphBody body,
      List<GraphRecipient> toRecipients,
      List<GraphRecipient> ccRecipients,
      List<GraphRecipient> bccRecipients,
      List<GraphHeader> internetMessageHeaders) {

    public GraphSendMessage(
        String subject,
        GraphBody body,
        List<GraphRecipient> toRecipients,
        List<GraphRecipient> ccRecipients,
        List<GraphRecipient> bccRecipients) {
      this(subject, body, toRecipients, ccRecipients, bccRecipients, null);
    }
  }

  public record GraphBatchRequest(List<GraphBatchSubRequest> requests) {}

  public record GraphBatchSubRequest(
      String id,
      String method,
      String url,
      Map<String, String> headers,
      Map<String, Object> body) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record GraphBatchResponse(List<GraphBatchSubResponse> responses) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record GraphBatchSubResponse(String id, Integer status) {}

  public record SubscriptionRequest(
      String changeType,
      String notificationUrl,
      String resource,
      String expirationDateTime,
      String clientState) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record SubscriptionResponse(
      String id, String resource, String expirationDateTime, String clientState) {
    public Instant expirationInstant() {
      return OffsetDateTime.parse(expirationDateTime).toInstant();
    }
  }
}
