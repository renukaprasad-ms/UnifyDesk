package com.messaging.channel.zoho;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.ServiceUnavailableException;
import com.messaging.common.exception.TooManyRequestsException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class ZohoMailApiClient {

  private final ZohoMailOAuthProperties properties;

  public ZohoMailApiClient(ZohoMailOAuthProperties properties) {
    this.properties = properties;
  }

  public String authorizationUrl(String state) {
    requireConfigured();
    String accountsBase =
        ZohoMailDomains.validateAuthorizationBase(properties.getAuthorizationAccountsBaseUrl());
    return UriComponentsBuilder.fromUriString(accountsBase + "/oauth/v2/auth")
        .queryParam("scope", properties.getScope())
        .queryParam("client_id", properties.getClientId())
        .queryParam("response_type", "code")
        .queryParam("access_type", "offline")
        .queryParam("prompt", "consent")
        .queryParam("redirect_uri", properties.getRedirectUri())
        .queryParam("state", state)
        .build()
        .encode()
        .toUriString();
  }

  public TokenResponse exchangeCode(String accountsServer, String code) {
    requireConfigured();
    String base = validatedAccountsServer(accountsServer);
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("client_id", properties.getClientId());
    form.add("client_secret", properties.getClientSecret());
    form.add("redirect_uri", properties.getRedirectUri());
    form.add("grant_type", "authorization_code");
    form.add("code", code);
    return oauth(
        "token_exchange",
        () ->
            RestClient.create(base)
                .post()
                .uri("/oauth/v2/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenResponse.class));
  }

  public TokenResponse refresh(String accountsServer, String refreshToken) {
    requireConfigured();
    String base = validatedAccountsServer(accountsServer);
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("client_id", properties.getClientId());
    form.add("client_secret", properties.getClientSecret());
    form.add("grant_type", "refresh_token");
    form.add("refresh_token", refreshToken);
    return oauth(
        "token_refresh",
        () ->
            RestClient.create(base)
                .post()
                .uri("/oauth/v2/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenResponse.class));
  }

  public List<ZohoAccount> accounts(String mailApiBaseUrl, String accessToken) {
    AccountsResponse response =
        api(
            "accounts",
            () ->
                client(mailApiBaseUrl)
                    .get()
                    .uri("/api/accounts")
                    .header(HttpHeaders.AUTHORIZATION, oauthHeader(accessToken))
                    .retrieve()
                    .body(AccountsResponse.class));
    return response == null || response.data() == null ? List.of() : response.data();
  }

  public List<ZohoFolder> folders(
      String mailApiBaseUrl, String accessToken, String accountId) {
    FoldersResponse response =
        api(
            "folders",
            () ->
                client(mailApiBaseUrl)
                    .get()
                    .uri("/api/accounts/{accountId}/folders", accountId)
                    .header(HttpHeaders.AUTHORIZATION, oauthHeader(accessToken))
                    .retrieve()
                    .body(FoldersResponse.class));
    return response == null || response.data() == null ? List.of() : response.data();
  }

  public List<ZohoMessage> messages(
      String mailApiBaseUrl,
      String accessToken,
      String accountId,
      String folderId,
      int start,
      int limit) {
    MessagesResponse response =
        api(
            "messages",
            () ->
                client(mailApiBaseUrl)
                    .get()
                    .uri(
                        builder ->
                            builder
                                .path("/api/accounts/{accountId}/messages/view")
                                .queryParam("folderId", folderId)
                                .queryParam("start", Math.max(1, start))
                                .queryParam("limit", Math.max(1, Math.min(200, limit)))
                                .queryParam("status", "all")
                                .queryParam("includeto", true)
                                .build(accountId))
                    .header(HttpHeaders.AUTHORIZATION, oauthHeader(accessToken))
                    .retrieve()
                    .body(MessagesResponse.class));
    return response == null || response.data() == null ? List.of() : response.data();
  }

  public String content(
      String mailApiBaseUrl,
      String accessToken,
      String accountId,
      String folderId,
      String messageId) {
    ContentResponse response =
        api(
            "message_content",
            () ->
                client(mailApiBaseUrl)
                    .get()
                    .uri(
                        "/api/accounts/{accountId}/folders/{folderId}/messages/{messageId}/content",
                        accountId,
                        folderId,
                        messageId)
                    .header(HttpHeaders.AUTHORIZATION, oauthHeader(accessToken))
                    .retrieve()
                    .body(ContentResponse.class));
    return response == null || response.data() == null ? null : response.data().content();
  }

  public SendData send(
      String mailApiBaseUrl,
      String accessToken,
      String accountId,
      SendRequest request) {
    SendResponse response =
        api(
            "send",
            () ->
                client(mailApiBaseUrl)
                    .post()
                    .uri("/api/accounts/{accountId}/messages", accountId)
                    .header(HttpHeaders.AUTHORIZATION, oauthHeader(accessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(SendResponse.class));
    if (response == null || response.data() == null || !hasText(response.data().messageId())) {
      throw new BadRequestException("Zoho Mail returned an invalid send response");
    }
    return response.data();
  }

  public void updateReadState(
      String mailApiBaseUrl,
      String accessToken,
      String accountId,
      List<String> messageIds,
      boolean read) {
    if (messageIds == null || messageIds.isEmpty()) return;
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("mode", read ? "markAsRead" : "markAsUnread");
    body.put("messageId", messageIds);
    apiVoid(
        "read_state",
        () ->
            client(mailApiBaseUrl)
                .put()
                .uri("/api/accounts/{accountId}/updatemessage", accountId)
                .header(HttpHeaders.AUTHORIZATION, oauthHeader(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity());
  }

  private RestClient client(String baseUrl) {
    if (!hasText(baseUrl) || !baseUrl.startsWith("https://")) {
      throw new BadRequestException("Zoho Mail API domain is invalid");
    }
    return RestClient.create(baseUrl);
  }

  private String validatedAccountsServer(String value) {
    for (String location : List.of("us", "in", "eu", "au", "jp", "ca", "cn", "ae", "sa")) {
      ZohoMailDomains.Region region = ZohoMailDomains.resolve(location, null);
      if (region.accountsServer().equalsIgnoreCase(strip(value))) return region.accountsServer();
    }
    throw new BadRequestException("Zoho accounts server is not supported");
  }

  private String strip(String value) {
    if (value == null) return "";
    String result = value.trim();
    while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
    return result;
  }

  private String oauthHeader(String token) {
    return "Zoho-oauthtoken " + token;
  }

  private <T> T oauth(String operation, Call<T> call) {
    return execute(operation, call);
  }

  private <T> T api(String operation, Call<T> call) {
    T response = execute(operation, call);
    validateZohoResponse(response);
    return response;
  }

  private void apiVoid(String operation, VoidCall call) {
    executeVoid(operation, call);
  }

  private <T> T execute(String operation, Call<T> call) {
    int maxAttempts = Math.max(1, properties.getApiRetryMaxAttempts());
    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      try {
        T value = call.execute();
        if (value == null) throw new BadRequestException("Zoho returned an empty response");
        return value;
      } catch (RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        if ((status == 429 || status >= 500) && attempt < maxAttempts) {
          sleep(backoff(attempt));
          continue;
        }
        if (status == 429) throw new TooManyRequestsException("Zoho Mail rate limit exceeded");
        if (status >= 500) {
          throw new ServiceUnavailableException("Zoho Mail is temporarily unavailable", exception);
        }
        if (status == 401 || status == 403) {
          throw new BadRequestException(
              "Zoho mailbox authorization is invalid or missing required permissions");
        }
        throw new BadRequestException(
            "Zoho request failed for " + operation + " with HTTP " + status);
      }
    }
    throw new BadRequestException("Zoho request failed for " + operation);
  }

  private void executeVoid(String operation, VoidCall call) {
    execute(
        operation,
        () -> {
          call.execute();
          return Boolean.TRUE;
        });
  }

  private void validateZohoResponse(Object response) {
    ZohoStatus status = null;
    if (response instanceof AccountsResponse value) status = value.status();
    else if (response instanceof FoldersResponse value) status = value.status();
    else if (response instanceof MessagesResponse value) status = value.status();
    else if (response instanceof ContentResponse value) status = value.status();
    else if (response instanceof SendResponse value) status = value.status();

    if (status != null && status.code() != null && status.code() >= 400) {
      throw new BadRequestException(
          "Zoho Mail API rejected the request"
              + (hasText(status.description()) ? ": " + status.description() : ""));
    }
  }

  private long backoff(int attempt) {
    Duration initial = properties.getApiRetryInitialBackoff();
    Duration max = properties.getApiRetryMaxBackoff();
    long initialMs = Math.max(100L, initial.toMillis());
    long maxMs = Math.max(initialMs, max.toMillis());
    long exponential = initialMs * (1L << Math.min(20, attempt - 1));
    return Math.min(maxMs, exponential + ThreadLocalRandom.current().nextLong(0, 501));
  }

  private void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new BadRequestException("Zoho request interrupted");
    }
  }

  private void requireConfigured() {
    if (!properties.configured()) {
      throw new BadRequestException(
          "Zoho Mail is not configured. Set the Zoho OAuth environment variables first");
    }
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  @FunctionalInterface
  private interface Call<T> {
    T execute();
  }

  @FunctionalInterface
  private interface VoidCall {
    void execute();
  }

  public record TokenResponse(
      @JsonProperty("access_token") String accessToken,
      @JsonProperty("refresh_token") String refreshToken,
      @JsonProperty("api_domain") String apiDomain,
      @JsonProperty("token_type") String tokenType,
      @JsonProperty("expires_in") Long expiresIn,
      String scope) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ZohoStatus(Integer code, String description) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ZohoAccount(
      String accountId,
      String primaryEmailAddress,
      String mailboxAddress,
      String displayName,
      String accountDisplayName,
      String type,
      Boolean enabled) {
    public String email() {
      return has(primaryEmailAddress) ? primaryEmailAddress : mailboxAddress;
    }

    public String resolvedDisplayName() {
      return has(displayName) ? displayName : accountDisplayName;
    }

    private static boolean has(String value) {
      return value != null && !value.isBlank();
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ZohoFolder(String folderId, String folderName, String folderType, String path) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ZohoMessage(
      String messageId,
      String threadId,
      String folderId,
      String subject,
      String summary,
      String sender,
      String fromAddress,
      String toAddress,
      String ccAddress,
      String bccAddress,
      String sentDateInGMT,
      String receivedTime,
      String status,
      @JsonProperty("hasAttachment") Object hasAttachment) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ContentData(String messageId, String content) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record SendData(
      String messageId,
      String mailId,
      String fromAddress,
      String toAddress,
      String subject,
      String content) {}

  public record SendRequest(
      String fromAddress,
      String toAddress,
      String ccAddress,
      String bccAddress,
      String subject,
      String content,
      String mailFormat,
      String inReplyTo,
      String refHeader) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record AccountsResponse(ZohoStatus status, List<ZohoAccount> data) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record FoldersResponse(ZohoStatus status, List<ZohoFolder> data) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record MessagesResponse(ZohoStatus status, List<ZohoMessage> data) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record ContentResponse(ZohoStatus status, ContentData data) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record SendResponse(ZohoStatus status, SendData data) {}
}
