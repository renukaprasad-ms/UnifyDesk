package com.messaging.managedsms.delivery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.messaging.broadcast.transport.BroadcastTransportException;
import com.messaging.managedsms.runtime.ManagedSmsRuntimeProperties;
import com.messaging.managedsms.runtime.ManagedSmsRuntimeRepository;
import com.messaging.managedsms.runtime.ManagedSmsTemplateService;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ManagedMsg91Gateway {

  private static final URI SEND_URI = URI.create("https://control.msg91.com/api/v5/flow");
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

  private final ObjectMapper objectMapper;
  private final ManagedSmsRuntimeProperties properties;

  private final HttpClient httpClient =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  public ManagedSmsSendResult send(
      ManagedSmsRuntimeRepository.DeliveryRuntime runtime,
      String destination,
      Map<String, String> variables,
      java.util.List<ManagedSmsTemplateService.VariableSpec> variableSpecs,
      String correlationToken) {
    if (!properties.providerConfigured()) {
      throw BroadcastTransportException.retryable(
          "MANAGED_SMS_PROVIDER_CAPACITY_UNAVAILABLE",
          "Managed SMS provider is unavailable",
          null);
    }
    String flowId = trim(runtime.providerFlowRefSnapshot());
    if (flowId == null) {
      throw BroadcastTransportException.permanent(
          "MANAGED_SMS_TEMPLATE_NOT_READY", "Managed SMS template is not ready", null);
    }

    ObjectNode payload = objectMapper.createObjectNode();
    payload.put("flow_id", flowId);
    payload.put("sender", runtime.headerSnapshot());
    ObjectNode recipient = payload.putArray("recipients").addObject();
    recipient.put("mobiles", normalizeMobile(destination));
    recipient.put("CRQID", correlationToken);
    for (ManagedSmsTemplateService.VariableSpec spec : variableSpecs) {
      String value = variables.get(spec.key());
      if (value != null) recipient.put(spec.providerKey(), value);
    }

    HttpRequest request =
        HttpRequest.newBuilder(SEND_URI)
            .timeout(REQUEST_TIMEOUT)
            .header("accept", "application/json")
            .header("authkey", properties.getMasterAuthKey().trim())
            .header("content-type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
            .build();

    try {
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      JsonNode body = parse(response.body());
      int status = response.statusCode();
      String type = body.path("type").asText("");
      String code = firstText(body, "code", "statusCode");
      String message = firstText(body, "message", "description", "error");
      if (status >= 200 && status < 300 && "success".equalsIgnoreCase(type)) {
        String requestId = firstText(body, "requestId", "request_id", "message");
        if (requestId == null) requestId = correlationToken;
        return new ManagedSmsSendResult(requestId, Instant.now());
      }
      String safeMessage =
          message == null ? "Managed SMS provider rejected the request" : message;
      if (status == 429) {
        throw BroadcastTransportException.retryable(
            "MANAGED_SMS_PROVIDER_RATE_LIMIT", safeMessage, null);
      }
      if (status >= 500) {
        throw BroadcastTransportException.unknown(
            "MANAGED_SMS_PROVIDER_UNKNOWN", "Managed SMS send outcome is unknown", null);
      }
      if ("301".equals(code) || safeMessage.toLowerCase().contains("insufficient balance")) {
        throw BroadcastTransportException.permanent(
            "MANAGED_SMS_PROVIDER_CREDIT", "Managed SMS provider capacity is unavailable", null);
      }
      throw BroadcastTransportException.permanent(
          "MANAGED_SMS_PROVIDER_REJECTED", "Managed SMS provider rejected the request", null);
    } catch (BroadcastTransportException exception) {
      throw exception;
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw BroadcastTransportException.unknown(
          "MANAGED_SMS_PROVIDER_UNKNOWN", "Managed SMS send outcome is unknown", exception);
    } catch (IOException exception) {
      throw BroadcastTransportException.unknown(
          "MANAGED_SMS_PROVIDER_UNKNOWN", "Managed SMS send outcome is unknown", exception);
    }
  }

  private JsonNode parse(String body) {
    if (body == null || body.isBlank()) return objectMapper.createObjectNode();
    try {
      return objectMapper.readTree(body);
    } catch (Exception exception) {
      return objectMapper.createObjectNode();
    }
  }

  private String firstText(JsonNode body, String... names) {
    for (String name : names) {
      String value = trim(body.path(name).asText(null));
      if (value != null) return value;
    }
    return null;
  }

  private String normalizeMobile(String value) {
    if (value == null) {
      throw BroadcastTransportException.permanent(
          "MANAGED_SMS_DESTINATION_INVALID", "SMS destination is invalid", null);
    }
    String digits = value.replaceAll("[^0-9]", "");
    if (digits.length() < 8 || digits.length() > 15) {
      throw BroadcastTransportException.permanent(
          "MANAGED_SMS_DESTINATION_INVALID", "SMS destination is invalid", null);
    }
    return digits;
  }

  private String trim(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
