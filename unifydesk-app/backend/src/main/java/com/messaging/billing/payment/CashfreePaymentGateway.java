package com.messaging.billing.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.common.exception.ConflictException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class CashfreePaymentGateway implements PaymentProviderGateway {

  private final CashfreeProperties properties;
  private final ObjectMapper objectMapper;

  @Override
  public Checkout createOrder(
      String orderId,
      BigDecimal amount,
      String currency,
      Customer customer,
      String returnUrl,
      String notifyUrl,
      String idempotencyKey) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("order_id", orderId);
    body.put("order_amount", amount);
    body.put("order_currency", currency);
    body.put("customer_details", customer(customer));
    body.put(
        "order_meta",
        Map.of(
            "return_url", returnUrl + "?order_id={order_id}",
            "notify_url", notifyUrl));
    JsonNode json = post("/pg/orders", body, idempotencyKey);
    return new Checkout(
        json.path("order_id").asText(orderId),
        json.path("payment_session_id").asText(null),
        json.path("order_status").asText(null));
  }

  @Override
  public List<RemotePayment> fetchOrderPayments(String orderId) {
    JsonNode root = get("/pg/orders/" + encode(orderId) + "/payments");
    List<RemotePayment> result = new ArrayList<>();
    if (!root.isArray()) return result;
    for (JsonNode p : root) {
      result.add(
          new RemotePayment(
              p.path("cf_payment_id").asText(null),
              p.path("payment_status").asText(null),
              decimal(p, "payment_amount"),
              p.path("payment_currency").asText(null),
              instant(p.path("payment_time").asText(null)),
              p.path("error_details").path("error_code").asText(null)));
    }
    return result;
  }

  @Override
  public RemoteRefund createRefund(
      String orderId, String refundId, BigDecimal amount, String note, String idempotencyKey) {
    JsonNode json =
        post(
            "/pg/orders/" + encode(orderId) + "/refunds",
            Map.of(
                "refund_id", refundId,
                "refund_amount", amount,
                "refund_note", note == null ? "UnifyDesk refund" : note),
            idempotencyKey);
    return new RemoteRefund(
        json.path("cf_refund_id").asText(null), json.path("refund_status").asText(null));
  }

  @Override
  public OnDemandSubscription createOnDemandSubscription(
      String subscriptionId,
      BigDecimal maxAmount,
      String currency,
      Customer customer,
      String returnUrl,
      String idempotencyKey) {
    Map<String, Object> plan = new LinkedHashMap<>();
    plan.put("plan_name", "UnifyDesk usage and subscription charges");
    plan.put("plan_type", "ON_DEMAND");
    plan.put("plan_currency", currency);
    plan.put("plan_amount", BigDecimal.ONE);
    plan.put("plan_max_amount", maxAmount);
    plan.put("plan_max_cycles", 0);
    plan.put("plan_note", "UnifyDesk on-demand billing mandate");
    JsonNode json =
        post(
            "/pg/subscriptions",
            Map.of(
                "subscription_id", subscriptionId,
                "customer_details", customer(customer),
                "plan_details", plan,
                "authorization_details",
                    Map.of("authorization_amount", BigDecimal.ONE),
                "subscription_meta", Map.of("return_url", returnUrl)),
            idempotencyKey);
    return new OnDemandSubscription(
        json.path("subscription_id").asText(subscriptionId),
        json.path("subscription_session_id").asText(null),
        json.path("authorization_details").path("authorization_status").asText(null));
  }

  @Override
  public OnDemandSubscription fetchSubscription(String subscriptionId) {
    JsonNode json = get("/pg/subscriptions/" + encode(subscriptionId));
    return new OnDemandSubscription(
        json.path("subscription_id").asText(subscriptionId),
        json.path("subscription_session_id").asText(null),
        json.path("authorization_details").path("authorization_status").asText(null));
  }

  private Map<String, Object> customer(Customer customer) {
    if (customer == null
        || blank(customer.id())
        || blank(customer.email())
        || blank(customer.phone())) {
      throw new IllegalArgumentException(
          "Cashfree customer id, email and phone are required for provider collection");
    }
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("customer_id", customer.id());
    result.put("customer_email", customer.email());
    result.put("customer_phone", customer.phone());
    if (!blank(customer.name())) result.put("customer_name", customer.name());
    return result;
  }

  private JsonNode post(String path, Object body, String idempotencyKey) {
    assertConfigured();
    String response =
        client()
            .post()
            .uri(path)
            .contentType(MediaType.APPLICATION_JSON)
            .header("x-client-id", properties.getClientId())
            .header("x-client-secret", properties.getClientSecret())
            .header("x-api-version", properties.getApiVersion())
            .header("x-idempotency-key", idempotencyKey)
            .body(body)
            .retrieve()
            .body(String.class);
    return read(response);
  }

  private JsonNode get(String path) {
    assertConfigured();
    String response =
        client()
            .get()
            .uri(path)
            .header("x-client-id", properties.getClientId())
            .header("x-client-secret", properties.getClientSecret())
            .header("x-api-version", properties.getApiVersion())
            .retrieve()
            .body(String.class);
    return read(response);
  }

  private RestClient client() {
    return RestClient.builder().baseUrl(properties.getBaseUrl()).build();
  }

  private void assertConfigured() {
    if (!properties.isEnabled()
        || blank(properties.getClientId())
        || blank(properties.getClientSecret())) {
      throw new ConflictException("Cashfree provider is not configured");
    }
    if (!"2026-01-01".equals(properties.getApiVersion())) {
      throw new ConflictException("Cashfree API version must remain pinned to 2026-01-01");
    }
  }

  private JsonNode read(String value) {
    try {
      return objectMapper.readTree(value == null ? "{}" : value);
    } catch (Exception exception) {
      throw new IllegalStateException("Cashfree returned an invalid JSON response", exception);
    }
  }

  private BigDecimal decimal(JsonNode node, String field) {
    return node.path(field).isNumber() ? node.path(field).decimalValue() : null;
  }

  private Instant instant(String value) {
    if (blank(value)) return null;
    try {
      return java.time.OffsetDateTime.parse(value).toInstant();
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  private String encode(String value) {
    return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
