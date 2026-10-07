package com.messaging.channel.zoho;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.messaging.channel.email.EmailAttachment;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.ServiceUnavailableException;
import com.messaging.common.exception.TooManyRequestsException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@RequiredArgsConstructor
public class ZohoMailApiClient {

  private final ZohoMailOAuthProperties properties;

  public String authorizationUrl(String state) {
    requireConfigured();
    String accountsServer = ZohoMailDataCenter.trustedAccountsServer(properties.getAccountsBaseUrl());
    return UriComponentsBuilder.fromUriString(accountsServer + "/oauth/v2/auth")
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

  public TokenResponse exchangeCode(String code, String callbackAccountsServer) {
    requireConfigured();
    String accountsServer = ZohoMailDataCenter.trustedAccountsServer(callbackAccountsServer);
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", "authorization_code");
    form.add("client_id", properties.getClientId());
    form.add("client_secret", properties.getClientSecret());
    form.add("redirect_uri", properties.getRedirectUri());
    form.add("code", code);

    return execute(
        "token_exchange",
        () ->
            RestClient.create(accountsServer)
                .post()
                .uri("/oauth/v2/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenResponse.class));
  }

  public TokenResponse refreshAccessToken(String accountsServer, String refreshToken) {
    requireConfigured();
    String trusted = ZohoMailDataCenter.trustedAccountsServer(accountsServer);
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", "refresh_token");
    form.add("client_id", properties.getClientId());
    form.add("client_secret", properties.getClientSecret());
    form.add("refresh_token", refreshToken);

    return execute(
        "token_refresh",
        () ->
            RestClient.create(trusted)
                .post()
                .uri("/oauth/v2/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenResponse.class));
  }

  public void revokeRefreshToken(String accountsServer, String refreshToken) {
    if (!hasText(refreshToken)) return;
    String trusted = ZohoMailDataCenter.trustedAccountsServer(accountsServer);
    executeVoid(
        "token_revoke",
        () ->
            RestClient.create(trusted)
                .post()
                .uri(
                    uriBuilder ->
                        uriBuilder
                            .path("/oauth/v2/token/revoke")
                            .queryParam("token", refreshToken)
                            .build())
                .retrieve()
                .toBodilessEntity());
  }

  public List<ZohoAccount> accounts(String accessToken, String mailBaseUrl) {
    AccountsResponse response =
        execute(
            "accounts",
            () ->
                mail(mailBaseUrl)
                    .get()
                    .uri("/api/accounts")
                    .header(HttpHeaders.AUTHORIZATION, authorization(accessToken))
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(AccountsResponse.class));
    return response == null || response.data() == null ? List.of() : response.data();
  }

  public List<ZohoFolder> folders(
      String accessToken, String mailBaseUrl, String accountId) {
    FoldersResponse response =
        execute(
            "folders",
            () ->
                mail(mailBaseUrl)
                    .get()
                    .uri("/api/accounts/{accountId}/folders", accountId)
                    .header(HttpHeaders.AUTHORIZATION, authorization(accessToken))
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(FoldersResponse.class));
    return response == null || response.data() == null ? List.of() : response.data();
  }

  public List<ZohoMessageSummary> listMessages(
      String accessToken,
      String mailBaseUrl,
      String accountId,
      String folderId,
      int start,
      int limit) {
    int pageSize = Math.max(1, Math.min(limit, 200));
    MessageListResponse response =
        execute(
            "messages_list",
            () ->
                mail(mailBaseUrl)
                    .get()
                    .uri(
                        uriBuilder ->
                            uriBuilder
                                .path("/api/accounts/{accountId}/messages/view")
                                .queryParam("folderId", folderId)
                                .queryParam("start", Math.max(1, start))
                                .queryParam("limit", pageSize)
                                .queryParam("status", "all")
                                .queryParam("sortBy", "date")
                                .queryParam("sortorder", false)
                                .queryParam("includeto", true)
                                .build(accountId))
                    .header(HttpHeaders.AUTHORIZATION, authorization(accessToken))
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(MessageListResponse.class));
    return response == null || response.data() == null ? List.of() : response.data();
  }

  public String content(
      String accessToken,
      String mailBaseUrl,
      String accountId,
      String folderId,
      String messageId) {
    ContentResponse response =
        execute(
            "message_content",
            () ->
                mail(mailBaseUrl)
                    .get()
                    .uri(
                        "/api/accounts/{accountId}/folders/{folderId}/messages/{messageId}/content",
                        accountId,
                        folderId,
                        messageId)
                    .header(HttpHeaders.AUTHORIZATION, authorization(accessToken))
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(ContentResponse.class));
    return response == null || response.data() == null ? null : response.data().content();
  }

  public Map<String, List<String>> headers(
      String accessToken,
      String mailBaseUrl,
      String accountId,
      String folderId,
      String messageId) {
    HeaderResponse response =
        execute(
            "message_headers",
            () ->
                mail(mailBaseUrl)
                    .get()
                    .uri(
                        uriBuilder ->
                            uriBuilder
                                .path(
                                    "/api/accounts/{accountId}/folders/{folderId}/messages/{messageId}/header")
                                .queryParam("raw", false)
                                .build(accountId, folderId, messageId))
                    .header(HttpHeaders.AUTHORIZATION, authorization(accessToken))
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(HeaderResponse.class));
    return response == null || response.data() == null || response.data().headerContent() == null
        ? Map.of()
        : response.data().headerContent();
  }

  public List<ZohoAttachmentInfo> attachmentInfo(
      String accessToken,
      String mailBaseUrl,
      String accountId,
      String folderId,
      String messageId) {
    AttachmentInfoResponse response =
        execute(
            "attachment_info",
            () ->
                mail(mailBaseUrl)
                    .get()
                    .uri(
                        uriBuilder ->
                            uriBuilder
                                .path(
                                    "/api/accounts/{accountId}/folders/{folderId}/messages/{messageId}/attachmentinfo")
                                .queryParam("includeInline", false)
                                .build(accountId, folderId, messageId))
                    .header(HttpHeaders.AUTHORIZATION, authorization(accessToken))
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(AttachmentInfoResponse.class));
    return response == null
            || response.data() == null
            || response.data().attachments() == null
        ? List.of()
        : response.data().attachments();
  }

  public void streamAttachment(
      String accessToken,
      String mailBaseUrl,
      String accountId,
      String folderId,
      String messageId,
      String attachmentId,
      OutputStream outputStream) {
    try {
      mail(mailBaseUrl)
          .get()
          .uri(
              "/api/accounts/{accountId}/folders/{folderId}/messages/{messageId}/attachments/{attachmentId}",
              accountId,
              folderId,
              messageId,
              attachmentId)
          .header(HttpHeaders.AUTHORIZATION, authorization(accessToken))
          .accept(MediaType.APPLICATION_OCTET_STREAM)
          .exchange(
              (request, response) -> {
                if (!response.getStatusCode().is2xxSuccessful()) {
                  throw new ServiceUnavailableException(
                      "Zoho Mail attachment download failed with HTTP "
                          + response.getStatusCode().value());
                }
                try {
                  response.getBody().transferTo(outputStream);
                  outputStream.flush();
                  return null;
                } catch (IOException exception) {
                  throw new ServiceUnavailableException(
                      "Unable to stream Zoho Mail attachment", exception);
                }
              });
    } catch (RestClientResponseException exception) {
      throw providerException("attachment_download", exception);
    }
  }

  public void updateReadState(
      String accessToken,
      String mailBaseUrl,
      String accountId,
      List<String> messageIds,
      boolean read) {
    if (messageIds == null || messageIds.isEmpty()) return;
    Map<String, Object> payload =
        Map.of(
            "mode", read ? "markAsRead" : "markAsUnread",
            "messageId", messageIds);

    executeVoid(
        "read_state",
        () ->
            mail(mailBaseUrl)
                .put()
                .uri("/api/accounts/{accountId}/updatemessage", accountId)
                .header(HttpHeaders.AUTHORIZATION, authorization(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toBodilessEntity());
  }

  public ZohoSentMessage send(
      String accessToken,
      String mailBaseUrl,
      String accountId,
      ZohoSendPayload payload) {
    SendResponse response =
        execute(
            "send",
            () ->
                mail(mailBaseUrl)
                    .post()
                    .uri("/api/accounts/{accountId}/messages", accountId)
                    .header(HttpHeaders.AUTHORIZATION, authorization(accessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(SendResponse.class));
    return response == null ? null : response.data();
  }

  public ZohoSentMessage reply(
      String accessToken,
      String mailBaseUrl,
      String accountId,
      String messageId,
      ZohoReplyPayload payload) {
    SendResponse response =
        execute(
            "reply",
            () ->
                mail(mailBaseUrl)
                    .post()
                    .uri("/api/accounts/{accountId}/messages/{messageId}", accountId, messageId)
                    .header(HttpHeaders.AUTHORIZATION, authorization(accessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(SendResponse.class));
    return response == null ? null : response.data();
  }

  public ZohoUploadedAttachment uploadAttachment(
      String accessToken,
      String mailBaseUrl,
      String accountId,
      EmailAttachment attachment) {
    byte[] bytes;
    try (var input = attachment.openStream()) {
      bytes = input.readAllBytes();
    } catch (IOException exception) {
      throw new ServiceUnavailableException("Unable to read email attachment", exception);
    }
    if (bytes.length != attachment.sizeBytes()) {
      throw new ServiceUnavailableException("Stored email attachment size changed");
    }

    UploadResponse response =
        execute(
            "attachment_upload",
            () ->
                mail(mailBaseUrl)
                    .post()
                    .uri(
                        uriBuilder ->
                            uriBuilder
                                .path("/api/accounts/{accountId}/messages/attachments")
                                .queryParam("fileName", attachment.fileName())
                                .queryParam("isInline", false)
                                .build(accountId))
                    .header(HttpHeaders.AUTHORIZATION, authorization(accessToken))
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(bytes)
                    .retrieve()
                    .body(UploadResponse.class));
    if (response == null || response.data() == null) {
      throw new ServiceUnavailableException("Zoho Mail did not return attachment metadata");
    }
    return response.data();
  }

  private RestClient mail(String mailBaseUrl) {
    return RestClient.create(trustedMailBase(mailBaseUrl));
  }

  private String trustedMailBase(String mailBaseUrl) {
    if (!hasText(mailBaseUrl)) {
      throw new BadRequestException("Zoho Mail API data center is missing");
    }
    URI uri;
    try {
      uri = URI.create(mailBaseUrl.trim());
    } catch (IllegalArgumentException exception) {
      throw new BadRequestException("Zoho Mail API data center is invalid");
    }
    if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
      throw new BadRequestException("Zoho Mail API data center is invalid");
    }
    String expected =
        ZohoMailDataCenter.mailBaseUrl(
            mailBaseUrl
                .replace("https://mail.", "https://accounts.")
                .replace("https://mail.zohocloud.ca", "https://accounts.zohocloud.ca"));
    if (!expected.equalsIgnoreCase(mailBaseUrl)) {
      throw new BadRequestException("Zoho Mail API data center is unsupported");
    }
    return expected;
  }

  private String authorization(String accessToken) {
    if (!hasText(accessToken)) {
      throw new BadRequestException("Zoho Mail access token is missing");
    }
    return "Zoho-oauthtoken " + accessToken.trim();
  }

  private <T> T execute(String operation, Supplier<T> request) {
    try {
      T response = request.get();
      if (response == null) {
        throw new ServiceUnavailableException("Zoho Mail returned an empty response");
      }
      return response;
    } catch (RestClientResponseException exception) {
      throw providerException(operation, exception);
    }
  }

  private void executeVoid(String operation, Runnable request) {
    try {
      request.run();
    } catch (RestClientResponseException exception) {
      throw providerException(operation, exception);
    }
  }

  private RuntimeException providerException(
      String operation, RestClientResponseException exception) {
    int status = exception.getStatusCode().value();
    if (status == 429) {
      return new TooManyRequestsException("Zoho Mail is temporarily rate limited");
    }
    if (status == 401 || status == 403) {
      return new BadRequestException(
          "Zoho Mail authorization is invalid or missing required permissions");
    }
    if (status >= 500) {
      return new ServiceUnavailableException(
          "Zoho Mail is temporarily unavailable during " + operation, exception);
    }
    return new BadRequestException(
        "Zoho Mail request failed during " + operation + " with HTTP " + status);
  }

  private void requireConfigured() {
    if (!properties.configured()) {
      throw new BadRequestException(
          "Zoho Mail is not configured. Configure Zoho OAuth before connecting an account");
    }
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record TokenResponse(
      @JsonProperty("access_token") String accessToken,
      @JsonProperty("refresh_token") String refreshToken,
      @JsonProperty("token_type") String tokenType,
      @JsonProperty("expires_in") Long expiresIn,
      @JsonProperty("api_domain") String apiDomain,
      String scope) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ZohoAccount(
      String accountId,
      String primaryEmailAddress,
      String displayName,
      String accountDisplayName,
      Integer sequence,
      Boolean enabled,
      Boolean status) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ZohoFolder(
      String folderId, String folderName, String folderType, String path) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ZohoMessageSummary(
      String messageId,
      String threadId,
      String folderId,
      String summary,
      String subject,
      String fromAddress,
      String sender,
      String toAddress,
      String ccAddress,
      String bccAddress,
      String receivedTime,
      String sentDateInGMT,
      String status,
      String hasAttachment,
      String size) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ZohoAttachmentInfo(
      String attachmentId, String attachmentName, Long attachmentSize) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ZohoUploadedAttachment(
      String storeName, String attachmentName, String attachmentPath, Long attachmentSize) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record ZohoSendPayload(
      String fromAddress,
      String toAddress,
      String ccAddress,
      String bccAddress,
      String subject,
      String content,
      String mailFormat,
      List<ZohoAttachmentReference> attachments) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record ZohoReplyPayload(
      String fromAddress,
      String toAddress,
      String ccAddress,
      String bccAddress,
      String subject,
      String content,
      String mailFormat,
      String action,
      List<ZohoAttachmentReference> attachments) {}

  public record ZohoAttachmentReference(
      String storeName, String attachmentName, String attachmentPath) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ZohoSentMessage(
      String messageId, String fromAddress, String toAddress, String subject) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record AccountsResponse(List<ZohoAccount> data) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record FoldersResponse(List<ZohoFolder> data) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record MessageListResponse(List<ZohoMessageSummary> data) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record ContentResponse(ContentData data) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record ContentData(String messageId, String content) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record HeaderResponse(HeaderData data) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record HeaderData(
      String messageId, Map<String, List<String>> headerContent) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record AttachmentInfoResponse(AttachmentInfoData data) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record AttachmentInfoData(
      String messageId,
      List<ZohoAttachmentInfo> attachments,
      List<ZohoAttachmentInfo> inline) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record SendResponse(ZohoSentMessage data) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record UploadResponse(ZohoUploadedAttachment data) {}
}
