package com.messaging.billing.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.common.exception.ConflictException;
import com.messaging.common.exception.ServiceUnavailableException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component
@RequiredArgsConstructor
public class CashfreePaymentGateway implements PaymentProviderGateway {

  private final CashfreeProperties properties;
  private final ObjectMapper objectMapper;
  private final java.util.Set<String> verifiedPlans = java.util.concurrent.ConcurrentHashMap.newKeySet();

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
    body.put("order_amount", cashfreeMoney(amount));
    body.put("order_currency", currency);
    body.put("customer_details", customer(customer));
    body.put(
        "order_meta",
        Map.of(
            "return_url", returnUrl + "?order_id={order_id}",
            "notify_url", notifyUrl));
    return checkout(
        paymentsPost("/pg/orders", body, idempotencyKey), orderId);
  }

  @Override
  public Checkout fetchOrder(String orderId) {
    try {
      return checkout(paymentsGet("/pg/orders/" + encode(orderId)), orderId);
    } catch (RestClientResponseException ex) {
      if (isAbsentResource(ex, "order_not_found", "order_does_not_exist")) {
        throw org.springframework.web.client.HttpClientErrorException.create(
            org.springframework.http.HttpStatus.NOT_FOUND,
            "Order not found",
            ex.getResponseHeaders(),
            ex.getResponseBodyAsByteArray(),
            null);
      }
      throw ex;
    }
  }

  @Override
  public List<RemotePayment> fetchOrderPayments(String orderId) {
    JsonNode root = paymentsGet("/pg/orders/" + encode(orderId) + "/payments");
    List<RemotePayment> result = new ArrayList<>();
    if (!root.isArray()) return result;
    for (JsonNode p : root) {
      result.add(
          new RemotePayment(
              text(p, "cf_payment_id"),
              text(p, "payment_status"),
              decimal(p, "payment_amount"),
              text(p, "payment_currency"),
              instant(text(p, "payment_time")),
              text(p.path("error_details"), "error_code")));
    }
    return result;
  }

  @Override
  public RemoteRefund createRefund(
      String orderId, String refundId, BigDecimal amount, String note, String idempotencyKey) {
    JsonNode json =
        paymentsPost(
            "/pg/orders/" + encode(orderId) + "/refunds",
            Map.of(
                "refund_id", refundId,
                "refund_amount", cashfreeMoney(amount),
                "refund_note", note == null ? "UnifyDesk refund" : note),
            idempotencyKey);
    return refund(json, refundId);
  }

  @Override
  public RemoteRefund fetchRefund(String orderId, String refundId) {
    return refund(
        paymentsGet(
            "/pg/orders/" + encode(orderId) + "/refunds/" + encode(refundId)),
        refundId);
  }

  private void ensurePlanExists(String planId, BigDecimal maxAmount, String currency) {
    if (verifiedPlans.contains(planId)) {
      return;
    }
    try {
      subscriptionsGet("/pg/plans/" + encode(planId));
      verifiedPlans.add(planId);
    } catch (RestClientResponseException ex) {
      boolean isNotFound = ex.getStatusCode().value() == 404
          || (ex.getStatusCode().value() == 400 && ex.getResponseBodyAsString().contains("plan_not_found"));
      if (isNotFound) {
        log.info("Plan {} not found in Cashfree, provisioning plan...", planId);
        Map<String, Object> planBody = new LinkedHashMap<>();
        planBody.put("plan_id", planId);
        planBody.put("plan_name", "UnifyDesk AutoPay " + currency + " " + maxAmount.intValue());
        planBody.put("plan_type", "ON_DEMAND");
        planBody.put("plan_currency", currency);
        planBody.put("plan_max_amount", cashfreeMoney(maxAmount));
        planBody.put("plan_note", "UnifyDesk on demand billing mandate");
        try {
          subscriptionsPost("/pg/plans", planBody, ProviderIdempotency.key("plan", planId));
        } catch (RestClientResponseException postEx) {
          if (postEx.getResponseBodyAsString().contains("plan_already_exists")) {
            log.info("Plan {} already exists in Cashfree", planId);
          } else {
            throw postEx;
          }
        }
        verifiedPlans.add(planId);
      } else {
        throw ex;
      }
    }
  }

  @Override
  public RecurringAuthorization createRecurringAuthorization(
      String subscriptionId,
      BigDecimal maxAmount,
      String currency,
      Customer customer,
      String returnUrl,
      String idempotencyKey) {
    String planId = "PLAN_ON_DEMAND_" + maxAmount.intValue() + "_" + currency;
    ensurePlanExists(planId, maxAmount, currency);

    JsonNode json =
        subscriptionsPost(
            "/pg/subscriptions",
            Map.of(
                "subscription_id", subscriptionId,
                "customer_details", customer(customer),
                "plan_details", Map.of("plan_id", planId),
                "authorization_details", Map.of("authorization_amount", cashfreeMoney(BigDecimal.ONE)),
                "subscription_meta", Map.of("return_url", returnUrl)),
            idempotencyKey);
    return authorization(json, subscriptionId);
  }

  @Override
  public RecurringAuthorization fetchRecurringAuthorization(String subscriptionId) {
    try {
      return authorization(
          subscriptionsGet("/pg/subscriptions/" + encode(subscriptionId)), subscriptionId);
    } catch (RestClientResponseException ex) {
      if (isAbsentResource(ex, "subscription_not_found")) {
        throw org.springframework.web.client.HttpClientErrorException.create(
            org.springframework.http.HttpStatus.NOT_FOUND,
            "Subscription not found",
            ex.getResponseHeaders(),
            ex.getResponseBodyAsByteArray(),
            null);
      }
      throw ex;
    }
  }

  @Override
  public RemoteSubscriptionPayment authorizeRecurringSubscription(
      String subscriptionId,
      String subscriptionSessionId,
      String paymentId,
      AuthorizationMethod method,
      String idempotencyKey) {
    if (method != AuthorizationMethod.HOSTED_UPI_LINK) {
      throw new IllegalArgumentException("Unsupported recurring authorization method");
    }
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("subscription_id", subscriptionId);
    body.put("payment_id", paymentId);
    body.put("payment_type", "AUTH");
    body.put("subscription_session_id", subscriptionSessionId);
    body.put("payment_method", Map.of("upi", Map.of("channel", "link")));
    return subscriptionPayment(
        subscriptionsPost("/pg/subscriptions/pay", body, idempotencyKey), paymentId);
  }

  @Override
  public RemoteSubscriptionPayment chargeAuthorizedSubscription(
      String subscriptionId,
      String paymentId,
      BigDecimal amount,
      Instant scheduledAt,
      String idempotencyKey) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("subscription_id", subscriptionId);
    body.put("payment_id", paymentId);
    body.put("payment_type", "CHARGE");
    body.put("payment_amount", cashfreeMoney(amount));
    Instant schedule = scheduledAt == null ? Instant.now() : scheduledAt;
    body.put(
        "payment_schedule_date",
        OffsetDateTime.ofInstant(schedule, ZoneOffset.ofHoursMinutes(5, 30)).toString());
    body.put("payment_remarks", "UnifyDesk canonical invoice collection");
    return subscriptionPayment(
        subscriptionsPost("/pg/subscriptions/pay", body, idempotencyKey), paymentId);
  }

  @Override
  public RemoteSubscriptionPayment fetchSubscriptionPayment(
      String subscriptionId, String paymentId) {
    try {
      return subscriptionPayment(
          subscriptionsGet(
              "/pg/subscriptions/"
                  + encode(subscriptionId)
                  + "/payments/"
                  + encode(paymentId)),
          paymentId);
    } catch (RestClientResponseException ex) {
      if (isAbsentResource(ex, "payment_id_not_found", "payment_not_found")) {
        throw org.springframework.web.client.HttpClientErrorException.create(
            org.springframework.http.HttpStatus.NOT_FOUND,
            "Payment not found",
            ex.getResponseHeaders(),
            ex.getResponseBodyAsByteArray(),
            null);
      }
      throw ex;
    }
  }

  private boolean isAbsentResource(RestClientResponseException ex, String... errorCodes) {
    if (ex.getStatusCode().value() == 404) {
      return true;
    }
    if (ex.getStatusCode().value() == 400) {
      String body = ex.getResponseBodyAsString();
      if (body != null) {
        for (String code : errorCodes) {
          if (body.contains(code)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private Checkout checkout(JsonNode json, String fallbackOrderId) {
    return new Checkout(
        json.path("order_id").asText(fallbackOrderId),
        text(json, "payment_session_id"),
        text(json, "order_status"));
  }

  private RemoteRefund refund(JsonNode json, String fallbackRefundId) {
    return new RemoteRefund(
        json.path("refund_id").asText(fallbackRefundId),
        text(json, "cf_refund_id"),
        text(json, "refund_status"),
        decimal(json, "refund_amount"),
        text(json, "refund_currency"),
        instant(text(json, "created_at")),
        instant(text(json, "processed_at")));
  }

  private RecurringAuthorization authorization(JsonNode json, String fallbackSubscriptionId) {
    JsonNode details = json.path("authorization_details");
    return new RecurringAuthorization(
        json.path("subscription_id").asText(fallbackSubscriptionId),
        text(json, "subscription_session_id"),
        text(details, "authorization_status"),
        text(json, "cf_subscription_id"));
  }

  private RemoteSubscriptionPayment subscriptionPayment(JsonNode json, String fallbackPaymentId) {
    JsonNode failure = json.path("failure_details");
    JsonNode data = json.path("data");
    String actionUrl = text(data, "url");
    if (blank(actionUrl)) actionUrl = text(json, "url");
    if (blank(actionUrl)) actionUrl = text(json, "payment_url");
    if (blank(actionUrl)) actionUrl = text(data, "payment_url");
    if (blank(actionUrl)) actionUrl = text(data, "link");
    if (blank(actionUrl) && data.has("payload")) {
      JsonNode payload = data.path("payload");
      actionUrl = text(payload, "default");
      if (blank(actionUrl)) actionUrl = text(payload, "url");
    }
    return new RemoteSubscriptionPayment(
        json.path("payment_id").asText(fallbackPaymentId),
        text(json, "cf_payment_id"),
        text(json, "payment_status"),
        decimal(json, "payment_amount"),
        text(json, "payment_currency"),
        instant(text(json, "payment_initiated_date")),
        actionUrl,
        text(failure, "failure_reason"));
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

  private JsonNode paymentsPost(String path, Object body, String idempotencyKey) {
    return post(
        path,
        body,
        idempotencyKey,
        properties.getPaymentsApiVersion(),
        properties.getClientId(),
        properties.getClientSecret());
  }

  private JsonNode subscriptionsPost(String path, Object body, String idempotencyKey) {
    return post(
        path,
        body,
        idempotencyKey,
        properties.getSubscriptionsApiVersion(),
        properties.getEffectiveSubscriptionsClientId(),
        properties.getEffectiveSubscriptionsClientSecret());
  }

  private JsonNode paymentsGet(String path) {
    return get(
        path,
        properties.getPaymentsApiVersion(),
        properties.getClientId(),
        properties.getClientSecret());
  }

  private JsonNode subscriptionsGet(String path) {
    return get(
        path,
        properties.getSubscriptionsApiVersion(),
        properties.getEffectiveSubscriptionsClientId(),
        properties.getEffectiveSubscriptionsClientSecret());
  }

  private JsonNode post(
      String path,
      Object body,
      String idempotencyKey,
      String apiVersion,
      String clientId,
      String clientSecret) {
    assertConfigured(apiVersion, clientId, clientSecret);
    try {
      String response =
          client()
              .post()
              .uri(path)
              .contentType(MediaType.APPLICATION_JSON)
              .header("x-client-id", clientId)
              .header("x-client-secret", clientSecret)
              .header("x-api-version", apiVersion)
              .header("x-idempotency-key", idempotencyKey)
              .body(body)
              .retrieve()
              .body(String.class);
      return read(response);
    } catch (RestClientResponseException exception) {
      String responseBody = exception.getResponseBodyAsString();
      log.error("Cashfree API POST {} failed: status={}, response={}", path, exception.getStatusCode(), responseBody);
      String detail = null;
      try {
        JsonNode errJson = objectMapper.readTree(responseBody);
        if (errJson.hasNonNull("message")) {
          detail = errJson.path("message").asText();
        }
      } catch (Exception ignored) {
      }
      String msg = detail != null && !detail.isBlank()
          ? "Cashfree rejected the payment request: " + detail
          : "Cashfree rejected the payment request. Please retry or check provider configuration.";
      throw new ServiceUnavailableException(msg, exception);
    }
  }

  private JsonNode get(
      String path,
      String apiVersion,
      String clientId,
      String clientSecret) {
    assertConfigured(apiVersion, clientId, clientSecret);
    try {
      String response =
          client()
              .get()
              .uri(path)
              .header("x-client-id", clientId)
              .header("x-client-secret", clientSecret)
              .header("x-api-version", apiVersion)
              .retrieve()
              .body(String.class);
      return read(response);
    } catch (RestClientResponseException exception) {
      log.debug("Cashfree API GET {} returned status={}", path, exception.getStatusCode());
      throw exception;
    }
  }

  private RestClient client() {
    return RestClient.builder().baseUrl(properties.getBaseUrl()).build();
  }

  private void assertConfigured(String apiVersion, String clientId, String clientSecret) {
    if (!properties.isEnabled()
        || blank(clientId)
        || blank(clientSecret)) {
      throw new ConflictException("Cashfree provider is not configured");
    }
    if (blank(apiVersion)) {
      throw new ConflictException("Cashfree API version is not configured for this operation");
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

  private String text(JsonNode node, String field) {
    String value = node.path(field).asText(null);
    return blank(value) ? null : value;
  }

  private Instant instant(String value) {
    if (blank(value)) return null;
    try {
      return OffsetDateTime.parse(value).toInstant();
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  private String encode(String value) {
    return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
  }

  static BigDecimal cashfreeMoney(BigDecimal amount) {
    if (amount == null) throw new IllegalArgumentException("Cashfree amount is required");
    return amount.setScale(2, RoundingMode.HALF_UP);
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
