package com.messaging.channel.microsoft;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.TooManyRequestsException;
import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
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
        hasText(continuationUrl) ? URI.create(continuationUrl) : initialDeltaUri(folderKey);

    return execute(
        "mail_delta",
        () ->
            graph
                .get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .header(
                    "Prefer",
                    "odata.maxpagesize=" + Math.max(1, Math.min(properties.getDeltaPageSize(), 200)))
                .retrieve()
                .body(DeltaResponse.class));
  }

  URI initialDeltaUri(String folderKey) {
    return UriComponentsBuilder.fromUriString(
            GRAPH_BASE + "/v1.0/me/mailFolders/{folder}/messages/delta")
        .queryParam("$select", MESSAGE_SELECT)
        .queryParam(
            "$expand",
            "attachments($select=id,name,contentType,size,isInline)")
        .queryParam("$top", Math.max(1, Math.min(properties.getDeltaPageSize(), 200)))
        .buildAndExpand(folderKey)
        .encode()
        .toUri();
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

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record SendMailRequest(GraphSendMessage message, Boolean saveToSentItems) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record GraphSendMessage(
      String subject,
      GraphBody body,
      List<GraphRecipient> toRecipients,
      List<GraphRecipient> ccRecipients,
      List<GraphRecipient> bccRecipients) {}

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
