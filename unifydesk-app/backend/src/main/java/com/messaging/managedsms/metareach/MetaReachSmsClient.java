package com.messaging.managedsms.metareach;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.broadcast.transport.BroadcastTransportException;
import com.messaging.managedsms.delivery.ManagedSmsSendResult;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Server-only MetaReach bulk-SMS API. Published production contract is HTTPS GET.
 * Do not log request URIs: they contain credentials and message content.
 */
@Service
public class MetaReachSmsClient {
  private static final String ROOT = "https://sms.metareach.in/vb/";
  private final MetaReachProperties config;
  private final ObjectMapper mapper;
  private final HttpClient client;

  public MetaReachSmsClient(MetaReachProperties config, ObjectMapper mapper) {
    this.config = config;
    this.mapper = mapper;
    this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
        .followRedirects(HttpClient.Redirect.NEVER).build();
  }

  public ManagedSmsSendResult send(String header, String destination, String body, String dltTemplateId) {
    if (!config.ready()) throw retry("MANAGED_SMS_PROVIDER_CAPACITY_UNAVAILABLE");
    if (header == null || !header.matches("[A-Z0-9]{6}")
        || dltTemplateId == null || !dltTemplateId.matches("[0-9]{8,25}")) {
      throw BroadcastTransportException.permanent("MANAGED_SMS_TEMPLATE_NOT_READY",
          "MetaReach DLT Header / template mapping is not ready", null);
    }
    if (body == null || body.isBlank() || body.length() > (isAscii(body) ? 1000 : 500)) {
      throw BroadcastTransportException.permanent("MANAGED_SMS_TEMPLATE_NOT_READY",
          "SMS exceeds the supported MetaReach message size", null);
    }
    String mobile = mobile(destination);
    Map<String,String> fields=new java.util.HashMap<>(Map.of(
        "apikey",config.getApiKey(),"senderid",header,"number",mobile,
        "message",body,"templateid",dltTemplateId,"route",config.getRouteId(),"format","json"));
    if (!isAscii(body)) fields.put("unicode","2");
    JsonNode response = request("apikey.php",fields,true);
    String code = response.path("code").asText("");
    if (!"011".equals(code) || !"success".equalsIgnoreCase(response.path("status").asText(""))) {
      if ("008".equals(code) || "009".equals(code)) {
        throw BroadcastTransportException.permanent("MANAGED_SMS_PROVIDER_CREDIT",
            "Managed SMS provider capacity is unavailable",null);
      }
      if (code.isBlank()) throw unknown();
      throw BroadcastTransportException.permanent("MANAGED_SMS_PROVIDER_REJECTED",
          "Managed SMS provider rejected the request",null);
    }
    String messageId = response.path("data").path("messageid").asText("");
    if (messageId.isBlank() || messageId.length()>255) throw unknown();
    return new ManagedSmsSendResult(messageId, Instant.now());
  }

  public Optional<BigDecimal> balance() {
    if (!config.ready()) return Optional.empty();
    try {
      JsonNode body = request("http-credit.php",Map.of("apikey",config.getApiKey(),
          "route_id",config.getRouteId(),"format","json"),false);
      if (!"success".equalsIgnoreCase(body.path("status").asText(""))
          || !"003".equals(body.path("code").asText(""))) return Optional.empty();
      BigDecimal balance = new BigDecimal(body.path("balance").asText(""));
      return balance.signum()>=0 ? Optional.of(balance) : Optional.empty();
    } catch (Exception ignored) { return Optional.empty(); }
  }

  public Optional<JsonNode> dlr(String messageId) {
    if (!config.ready() || messageId == null || !messageId.matches("[A-Za-z0-9_+=/-]{1,255}"))
      return Optional.empty();
    try {
      JsonNode body = request("http-dlr.php", Map.of("apikey", config.getApiKey(),
          "msgid",messageId,"format","json"),false);
      if (!"success".equalsIgnoreCase(body.path("status").asText(""))
          || !body.path("data").isArray()) return Optional.empty();
      return Optional.of(body);
    } catch (Exception ignored) { return Optional.empty(); }
  }

  private JsonNode request(String endpoint, Map<String,String> params, boolean submission) {
    StringBuilder uri = new StringBuilder(ROOT).append(endpoint).append('?');
    params.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
      if (uri.charAt(uri.length()-1) != '?') uri.append('&');
      uri.append(encode(entry.getKey())).append('=').append(encode(entry.getValue()));
    });
    try {
      HttpRequest request = HttpRequest.newBuilder(URI.create(uri.toString()))
          .GET().timeout(Duration.ofSeconds(20)).header("accept","application/json").build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode()==429) throw retry("MANAGED_SMS_PROVIDER_RATE_LIMIT");
      if (response.statusCode()<200 || response.statusCode()>=300) {
        if (submission && response.statusCode()>=500) throw unknown();
        throw retry("MANAGED_SMS_PROVIDER_CAPACITY_UNAVAILABLE");
      }
      if (response.body()==null || response.body().length()>1_000_000) {
        if (submission) throw unknown();
        throw retry("MANAGED_SMS_PROVIDER_CAPACITY_UNAVAILABLE");
      }
      JsonNode json = mapper.readTree(response.body());
      if (json == null || !json.isObject()) {
        if (submission) throw unknown();
        throw retry("MANAGED_SMS_PROVIDER_CAPACITY_UNAVAILABLE");
      }
      return json;
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      if (submission) throw unknown();
      throw retry("MANAGED_SMS_PROVIDER_CAPACITY_UNAVAILABLE");
    } catch (IOException ex) {
      if (submission) throw unknown();
      throw retry("MANAGED_SMS_PROVIDER_CAPACITY_UNAVAILABLE");
    }
  }

  static String mobile(String raw) {
    if (raw == null) throw invalidNumber();
    String digits=raw.replaceAll("[^0-9]","");
    if (digits.length()==10 && digits.matches("[6-9][0-9]{9}")) return "91"+digits;
    if (digits.matches("91[6-9][0-9]{9}")) return digits;
    throw invalidNumber();
  }
  private static RuntimeException invalidNumber() {
    return BroadcastTransportException.permanent("MANAGED_SMS_DESTINATION_INVALID",
      "SMS destination is invalid",null);
  }
  private static boolean isAscii(String value) { return value.codePoints().allMatch(c -> c>=32 && c<=126); }
  private static String encode(String value) { return URLEncoder.encode(value,StandardCharsets.UTF_8); }
  private static BroadcastTransportException retry(String code) {
    return BroadcastTransportException.retryable(code,"Managed SMS provider is temporarily unavailable",null);
  }
  private static BroadcastTransportException unknown() {
    return BroadcastTransportException.unknown("MANAGED_SMS_PROVIDER_UNKNOWN",
        "Managed SMS send outcome is unknown",null);
  }
}
