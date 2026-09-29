package com.messaging.channel.meta.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class MetaWhatsAppRuntimeGraphClient {
  private final RestClient restClient;
  private final ObjectMapper objectMapper;
  private final MetaWhatsAppProviderFailureClassifier failureClassifier;

  public MetaWhatsAppRuntimeGraphClient(
      MetaWhatsAppRuntimeProperties properties,
      ObjectMapper objectMapper,
      MetaWhatsAppProviderFailureClassifier failureClassifier) {
    this.objectMapper = objectMapper;
    this.failureClassifier = failureClassifier;
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(properties.getProviderConnectTimeout());
    factory.setReadTimeout(properties.getProviderReadTimeout());
    this.restClient =
        RestClient.builder().baseUrl(properties.getGraphBaseUrl()).requestFactory(factory).build();
  }

  public SendResult sendText(
      MetaWhatsAppCredentialResolver.StoredCredential credential,
      String to,
      String text,
      String replyToProviderMessageId) {
    java.util.LinkedHashMap<String, Object> payload = new java.util.LinkedHashMap<>();
    payload.put("messaging_product", "whatsapp");
    payload.put("to", to);
    payload.put("type", "text");
    payload.put("text", Map.of("body", text));
    if (StringUtils.hasText(replyToProviderMessageId)) {
      payload.put("context", Map.of("message_id", replyToProviderMessageId));
    }
    return sendMessage(credential, payload);
  }

  public SendResult sendTemplate(
      MetaWhatsAppCredentialResolver.StoredCredential credential,
      String to,
      String name,
      String language,
      List<Map<String, Object>> components) {
    java.util.LinkedHashMap<String, Object> template = new java.util.LinkedHashMap<>();
    template.put("name", name);
    template.put("language", Map.of("code", language));
    if (components != null && !components.isEmpty()) template.put("components", components);
    return sendMessage(
        credential,
        Map.of(
            "messaging_product", "whatsapp",
            "to", to,
            "type", "template",
            "template", template));
  }

  public SendResult sendMedia(
      MetaWhatsAppCredentialResolver.StoredCredential credential,
      String to,
      String mediaType,
      String providerMediaId,
      String caption,
      String replyToProviderMessageId) {
    java.util.LinkedHashMap<String, Object> media = new java.util.LinkedHashMap<>();
    media.put("id", providerMediaId);
    if (StringUtils.hasText(caption)) media.put("caption", caption);
    java.util.LinkedHashMap<String, Object> payload = new java.util.LinkedHashMap<>();
    payload.put("messaging_product", "whatsapp");
    payload.put("to", to);
    payload.put("type", mediaType);
    payload.put(mediaType, media);
    if (StringUtils.hasText(replyToProviderMessageId)) {
      payload.put("context", Map.of("message_id", replyToProviderMessageId));
    }
    return sendMessage(credential, payload);
  }

  public String uploadMedia(
      MetaWhatsAppCredentialResolver.StoredCredential credential,
      byte[] bytes,
      String fileName,
      String contentType) {
    MultipartBodyBuilder builder = new MultipartBodyBuilder();
    builder.part("messaging_product", "whatsapp");
    builder
        .part(
            "file",
            new ByteArrayResource(bytes) {
              @Override
              public String getFilename() {
                return fileName;
              }
            })
        .contentType(MediaType.parseMediaType(contentType));
    JsonNode response =
        execute(
            false,
            () ->
                restClient
                    .post()
                    .uri(
                        "/{version}/{phoneNumberId}/media",
                        credential.graphApiVersion(),
                        credential.phoneNumberId())
                    .header(HttpHeaders.AUTHORIZATION, bearer(credential.accessToken()))
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(builder.build())
                    .retrieve()
                    .body(JsonNode.class));
    String id = text(response, "id");
    if (!StringUtils.hasText(id)) throw providerError(null, false, false, null);
    return id;
  }

  public List<TemplateRecord> listTemplates(
      MetaWhatsAppCredentialResolver.StoredCredential credential) {
    if (!StringUtils.hasText(credential.wabaId())) return List.of();
    List<TemplateRecord> result = new ArrayList<>();
    String next =
        "/"
            + credential.graphApiVersion()
            + "/"
            + credential.wabaId()
            + "/message_templates?fields=id,name,language,category,status,components&limit=250";
    int pages = 0;
    while (StringUtils.hasText(next) && pages++ < 20) {
      String current = next;
      JsonNode response =
          execute(
              false,
              () ->
                  restClient
                      .get()
                      .uri(current)
                      .header(HttpHeaders.AUTHORIZATION, bearer(credential.accessToken()))
                      .retrieve()
                      .body(JsonNode.class));
      JsonNode data = response == null ? null : response.path("data");
      if (data != null && data.isArray()) {
        for (JsonNode item : data) {
          result.add(
              new TemplateRecord(
                  text(item, "id"),
                  text(item, "name"),
                  text(item, "language"),
                  text(item, "category"),
                  text(item, "status"),
                  item.path("components")));
        }
      }
      next = response == null ? null : response.path("paging").path("next").asText(null);
    }
    return List.copyOf(result);
  }

  public MediaMetadata mediaMetadata(
      MetaWhatsAppCredentialResolver.StoredCredential credential, String providerMediaId) {
    JsonNode response =
        execute(
            false,
            () ->
                restClient
                    .get()
                    .uri(
                        "/{version}/{mediaId}",
                        credential.graphApiVersion(),
                        providerMediaId)
                    .header(HttpHeaders.AUTHORIZATION, bearer(credential.accessToken()))
                    .retrieve()
                    .body(JsonNode.class));
    return new MediaMetadata(
        text(response, "url"),
        text(response, "mime_type"),
        response == null || !response.path("file_size").canConvertToLong()
            ? null
            : response.path("file_size").asLong(),
        text(response, "sha256"));
  }

  public byte[] downloadAuthenticated(
      String url, String accessToken, long hardLimitBytes) {
    URI uri = URI.create(url);
    validateProviderMediaUri(uri);
    return execute(
        false,
        () ->
            restClient
                .get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .exchange(
                    (request, response) -> {
                      if (response.getStatusCode().isError()) {
                        throw providerError(response.getStatusCode().value(), true, false, null);
                      }
                      long declared = response.getHeaders().getContentLength();
                      if (declared > hardLimitBytes) {
                        throw new MetaWhatsAppRuntimeException(
                            "WHATSAPP_MEDIA_INVALID", false, "Provider media is too large");
                      }
                      try (InputStream input = response.getBody()) {
                        byte[] bytes = input.readNBytes(Math.toIntExact(hardLimitBytes + 1));
                        if (bytes.length > hardLimitBytes) {
                          throw new MetaWhatsAppRuntimeException(
                              "WHATSAPP_MEDIA_INVALID", false, "Provider media is too large");
                        }
                        return bytes;
                      }
                    }));
  }

  private SendResult sendMessage(
      MetaWhatsAppCredentialResolver.StoredCredential credential, Object payload) {
    JsonNode response =
        execute(
            true,
            () ->
                restClient
                    .post()
                    .uri(
                        "/{version}/{phoneNumberId}/messages",
                        credential.graphApiVersion(),
                        credential.phoneNumberId())
                    .header(HttpHeaders.AUTHORIZATION, bearer(credential.accessToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(JsonNode.class));
    JsonNode messages = response == null ? null : response.path("messages");
    String id =
        messages != null && messages.isArray() && !messages.isEmpty()
            ? text(messages.get(0), "id")
            : null;
    if (!StringUtils.hasText(id)) throw providerError(null, false, true, null);
    return new SendResult(id);
  }

  private <T> T execute(boolean ambiguousOnIo, GraphCall<T> call) {
    try {
      T result = call.execute();
      if (result == null) throw providerError(null, true, ambiguousOnIo, null);
      return result;
    } catch (MetaWhatsAppProviderException | MetaWhatsAppRuntimeException exception) {
      throw exception;
    } catch (RestClientResponseException exception) {
      throw providerError(
          exception.getStatusCode().value(),
          exception.getStatusCode().value() == 429 || exception.getStatusCode().is5xxServerError(),
          false,
          exception);
    } catch (ResourceAccessException exception) {
      throw providerError(null, true, ambiguousOnIo, exception);
    }
  }

  private MetaWhatsAppProviderException providerError(
      Integer status, boolean retryable, boolean ambiguous, Throwable cause) {
    Integer code = null;
    Integer subcode = null;
    String trace = null;
    if (cause instanceof RestClientResponseException response) {
      try {
        JsonNode error = objectMapper.readTree(response.getResponseBodyAsByteArray()).path("error");
        if (error.path("code").canConvertToInt()) code = error.path("code").asInt();
        if (error.path("error_subcode").canConvertToInt()) subcode = error.path("error_subcode").asInt();
        trace = error.path("fbtrace_id").asText(null);
      } catch (Exception ignored) {
        // Deliberately do not retain raw provider response.
      }
    }
    MetaWhatsAppProviderFailureClassifier.Failure classified =
        failureClassifier.classify(status, status == null, ambiguous);
    return new MetaWhatsAppProviderException(
        status,
        code,
        subcode,
        trace,
        classified.safeCategory(),
        retryable && classified.retryable(),
        ambiguous && classified.ambiguousOutcome(),
        cause);
  }

  private void validateProviderMediaUri(URI uri) {
    String host = uri.getHost();
    if (!"https".equalsIgnoreCase(uri.getScheme())
        || host == null
        || !(ownedHost(host, "facebook.com")
            || ownedHost(host, "fbcdn.net")
            || ownedHost(host, "whatsapp.net")
            || ownedHost(host, "fbsbx.com"))) {
      throw new MetaWhatsAppRuntimeException(
          "WHATSAPP_MEDIA_INVALID", false, "Provider media URL is not trusted");
    }
  }

  private boolean ownedHost(String host, String suffix) {
    String normalized = host.toLowerCase(java.util.Locale.ROOT);
    return normalized.equals(suffix) || normalized.endsWith("." + suffix);
  }

  private String bearer(String token) {
    return "Bearer " + token;
  }

  private String text(JsonNode node, String field) {
    if (node == null) return null;
    JsonNode value = node.get(field);
    return value == null || !value.isValueNode() || value.asText().isBlank()
        ? null
        : value.asText().trim();
  }

  @FunctionalInterface
  private interface GraphCall<T> {
    T execute();
  }

  public record SendResult(String providerMessageId) {}

  public record TemplateRecord(
      String providerTemplateId,
      String name,
      String language,
      String category,
      String status,
      JsonNode components) {}

  public record MediaMetadata(String url, String mimeType, Long fileSize, String sha256) {}
}
