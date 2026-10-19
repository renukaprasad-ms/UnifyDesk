package com.messaging.billing.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.common.exception.ConflictException;
import com.messaging.common.exception.ServiceUnavailableException;
import com.messaging.common.id.IdGenerator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component
@org.springframework.context.annotation.Primary
@RequiredArgsConstructor
public class RazorpayPaymentGateway implements PaymentProviderGateway {

  private final RazorpayProperties properties;
  private final ObjectMapper objectMapper;
  private final JdbcTemplate jdbcTemplate;
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
    long amountInPaise = toPaise(amount);
    String receipt = "UD-" + sanitizeReceipt(orderId);

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("amount", amountInPaise);
    body.put("currency", currency != null && !currency.isBlank() ? currency.toUpperCase() : "INR");
    body.put("receipt", receipt);

    Map<String, String> notes = new LinkedHashMap<>();
    notes.put("internal_order_id", orderId);
    if (customer != null) {
      if (customer.id() != null) notes.put("customer_id", customer.id());
      if (customer.email() != null) notes.put("customer_email", customer.email());
      if (customer.phone() != null) notes.put("customer_phone", customer.phone());
      if (customer.name() != null) notes.put("customer_name", customer.name());
    }
    body.put("notes", notes);

    JsonNode json = post("/orders", body);
    String rzpOrderId = text(json, "id");
    String status = text(json, "status");

    return new Checkout(orderId, rzpOrderId, status != null ? status : "created");
  }

  @Override
  public Checkout fetchOrder(String orderId) {
    try {
      JsonNode json = get("/orders/" + encode(orderId));
      return new Checkout(
          orderId,
          text(json, "id"),
          text(json, "status"));
    } catch (RestClientResponseException ex) {
      if (ex.getStatusCode().value() == 404) {
        throw HttpClientErrorException.create(
            HttpStatus.NOT_FOUND,
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
    List<RemotePayment> result = new ArrayList<>();
    try {
      JsonNode json = get("/orders/" + encode(orderId) + "/payments");
      JsonNode items = json.path("items");
      if (items.isArray()) {
        for (JsonNode p : items) {
          result.add(
              new RemotePayment(
                  text(p, "id"),
                  mapPaymentStatus(text(p, "status")),
                  fromPaise(p.path("amount").asLong(0)),
                  text(p, "currency"),
                  instantFromEpochSeconds(p.path("created_at").asLong(0)),
                  text(p.path("error_code"), "code")));
        }
      }
    } catch (Exception e) {
      log.warn("Could not fetch payments for Razorpay order {}: {}", orderId, e.getMessage());
    }
    return result;
  }

  @Override
  public RemoteRefund createRefund(
      String orderId, String refundId, BigDecimal amount, String note, String idempotencyKey) {
    List<RemotePayment> payments = fetchOrderPayments(orderId);
    String paymentIdToRefund = null;
    for (RemotePayment rp : payments) {
      if ("SUCCESS".equalsIgnoreCase(rp.status())) {
        paymentIdToRefund = rp.providerPaymentId();
        break;
      }
    }

    if (paymentIdToRefund == null) {
      throw new ConflictException("No successful payment found on Razorpay order to refund: " + orderId);
    }

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("amount", toPaise(amount));
    body.put("notes", Map.of("refund_id", refundId, "note", note != null ? note : "UnifyDesk refund"));

    JsonNode json = post("/payments/" + encode(paymentIdToRefund) + "/refund", body);
    return new RemoteRefund(
        refundId,
        text(json, "id"),
        mapRefundStatus(text(json, "status")),
        fromPaise(json.path("amount").asLong(0)),
        text(json, "currency"),
        instantFromEpochSeconds(json.path("created_at").asLong(0)),
        Instant.now());
  }

  @Override
  public RemoteRefund fetchRefund(String orderId, String refundId) {
    try {
      JsonNode json = get("/refunds/" + encode(refundId));
      return new RemoteRefund(
          refundId,
          text(json, "id"),
          mapRefundStatus(text(json, "status")),
          fromPaise(json.path("amount").asLong(0)),
          text(json, "currency"),
          instantFromEpochSeconds(json.path("created_at").asLong(0)),
          Instant.now());
    } catch (RestClientResponseException ex) {
      if (ex.getStatusCode().value() == 404) {
        throw HttpClientErrorException.create(
            HttpStatus.NOT_FOUND,
            "Refund not found",
            ex.getResponseHeaders(),
            ex.getResponseBodyAsByteArray(),
            null);
      }
      throw ex;
    }
  }

  @Override
  public RecurringAuthorization createRecurringAuthorization(
      String subscriptionId, BigDecimal maxAmount, String currency,
      Customer customer, String returnUrl, String idempotencyKey) {
    assertRecurringEnabled();
    if (customer == null || customer.email() == null || customer.phone() == null
        || !"INR".equalsIgnoreCase(currency) || maxAmount == null
        || maxAmount.signum() <= 0 || maxAmount.compareTo(new BigDecimal("15000")) > 0) {
      throw new ConflictException("Valid INR mandate amount and billing contact are required");
    }
    String customerId = getOrCreateRazorpayCustomer(customer);
    if (customerId == null || !customerId.startsWith("cust_")) {
      throw new ConflictException("Razorpay customer registration is incomplete");
    }
    Map<String, Object> orderBody = new LinkedHashMap<>();
    orderBody.put("amount", 100L);
    orderBody.put("currency", "INR");
    orderBody.put("method", "upi");
    orderBody.put("customer_id", customerId);
    orderBody.put("receipt", "mandate-" + sanitizeReceipt(subscriptionId));
    orderBody.put("token", Map.of(
        "max_amount", toPaise(maxAmount),
        "expire_at", Instant.now().plus(java.time.Duration.ofDays(365 * 3)).getEpochSecond(),
        "frequency", "as_presented"));
    orderBody.put("notes", Map.of("type", "MANDATE_AUTH", "mandate_id", subscriptionId));
    String orderId = text(post("/orders", orderBody), "id");
    if (orderId == null || !orderId.startsWith("order_")) {
      throw new ConflictException("Razorpay did not return an authorization order");
    }
    return new RecurringAuthorization(subscriptionId, orderId, "PENDING_AUTH", customerId);
  }

  @Override
  public RecurringAuthorization fetchRecurringAuthorization(String subscriptionId) {
    if (subscriptionId != null && subscriptionId.startsWith("order_")) {
      try {
        Checkout order = fetchOrder(subscriptionId);
        boolean paid = "paid".equalsIgnoreCase(order.status());
        return new RecurringAuthorization(
            subscriptionId,
            order.paymentSessionId(),
            paid ? "ACTIVE" : "PENDING_AUTH",
            null);
      } catch (Exception ignored) {
        return new RecurringAuthorization(
            subscriptionId,
            subscriptionId,
            "PENDING_AUTH",
            null);
      }
    }
    if (subscriptionId != null && subscriptionId.startsWith("sub_")) {
      try {
        JsonNode json = get("/subscriptions/" + encode(subscriptionId));
        String status = text(json, "status");
        boolean active = "active".equalsIgnoreCase(status) || "authenticated".equalsIgnoreCase(status);
        return new RecurringAuthorization(
            subscriptionId,
            text(json, "id"),
            active ? "ACTIVE" : (status != null ? status.toUpperCase() : "PENDING_AUTH"),
            text(json, "short_url"));
      } catch (Exception ex) {
        log.warn("Could not fetch remote subscription {}: {}", subscriptionId, ex.getMessage());
      }
    }
    return new RecurringAuthorization(
        subscriptionId != null ? subscriptionId : "",
        subscriptionId != null ? subscriptionId : "",
        "PENDING_AUTH",
        null);
  }

  @Override
  public String verifyMandateToken(
      String orderId, String paymentId, String signature, String customerId) {
    if (signature != null && !signature.isBlank() && orderId != null && paymentId != null) {
      String expected = hmacSha256(orderId + "|" + paymentId, properties.getKeySecret());
      if (!expected.equalsIgnoreCase(signature)) {
        log.error("Razorpay signature mismatch for order {} payment {}: expected {}, received {}",
            orderId, paymentId, expected, signature);
        throw new ConflictException("Razorpay mandate payment signature verification failed");
      }
    }

    try {
      JsonNode p = get("/payments/" + encode(paymentId));
      String status = text(p, "status");
      log.info("Razorpay mandate authorization payment {}: status={}", paymentId, status);
      if ("authorized".equalsIgnoreCase(status)) {
        try {
          post("/payments/" + encode(paymentId) + "/capture",
              Map.of("amount", p.path("amount").asLong(100), "currency", "INR"));
        } catch (Exception capEx) {
          log.warn("Could not capture auth payment {}: {}", paymentId, capEx.getMessage());
        }
      }

      String tokenId = text(p, "token_id");
      if (tokenId == null && customerId != null && !customerId.isBlank()) {
        try {
          JsonNode tokensJson = get("/customers/" + encode(customerId) + "/tokens");
          JsonNode items = tokensJson.path("items");
          if (items.isArray() && !items.isEmpty()) {
            tokenId = text(items.get(items.size() - 1), "id");
            log.info("Resolved token {} for customer {}", tokenId, customerId);
          }
        } catch (Exception tokEx) {
          log.warn("Could not fetch tokens for customer {}: {}", customerId, tokEx.getMessage());
        }
      }
      return tokenId != null ? tokenId : paymentId;
    } catch (Exception e) {
      log.warn("Could not fetch or verify payment {} on Razorpay: {}", paymentId, e.getMessage());
      return paymentId;
    }
  }

  @Override
  public RemoteSubscriptionPayment authorizeRecurringSubscription(
      String subscriptionId,
      String subscriptionSessionId,
      String paymentId,
      AuthorizationMethod method,
      String idempotencyKey) {
    RecurringAuthorization auth = fetchRecurringAuthorization(subscriptionId);
    return new RemoteSubscriptionPayment(
        paymentId,
        auth.subscriptionSessionId(),
        auth.authorizationStatus(),
        BigDecimal.ONE,
        "INR",
        Instant.now(),
        auth.providerSubscriptionReference(),
        null);
  }

  @Override
  public RemoteSubscriptionPayment chargeAuthorizedSubscription(
      String subscriptionId,
      String paymentId,
      BigDecimal amount,
      Instant scheduledAt,
      String idempotencyKey) {
    log.info("Executing real Razorpay mandate charge for payment {} amount {} (mandateRef={})", paymentId, amount, subscriptionId);

    // 1. Resolve Mandate metadata from local DB
    String customerId = null;
    String tokenId = null;
    String email = null;
    String phone = null;

    if (jdbcTemplate != null) {
      try {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            select ppa.provider_subscription_id, ppa.provider_authorization_payment_id,
                   ppa.payment_method_type, ppa.company_id,
                   cbp.billing_email, cbp.billing_phone, c.business_email
              from payment_provider_authorizations ppa
              left join company_billing_profiles cbp on cbp.company_id = ppa.company_id
              left join companies c on c.id = ppa.company_id
             where ppa.provider_subscription_id = ?
                or ppa.provider_authorization_payment_id = ?
                or ppa.provider_subscription_session_id = ?
             order by case when ppa.authorization_status = 'ACTIVE' then 0 else 1 end, ppa.id desc
             limit 1
            """,
            subscriptionId, subscriptionId, subscriptionId);

        if (!rows.isEmpty()) {
          Map<String, Object> row = rows.getFirst();
          String methodType = (String) row.get("payment_method_type");
          if (methodType != null && methodType.startsWith("cust_")) {
            customerId = methodType;
          }
          String subId = (String) row.get("provider_subscription_id");
          if (subId != null && subId.startsWith("token_")) {
            tokenId = subId;
          }
          String authPayId = (String) row.get("provider_authorization_payment_id");
          if (tokenId == null && authPayId != null && authPayId.startsWith("token_")) {
            tokenId = authPayId;
          }

          email = (String) row.get("billing_email");
          if (email == null || email.isBlank()) {
            email = (String) row.get("business_email");
          }
          phone = (String) row.get("billing_phone");
        }
      } catch (Exception dbEx) {
        log.warn("Could not query authorization row for mandate {}: {}", subscriptionId, dbEx.getMessage());
      }
    }

    if (subscriptionId != null && subscriptionId.startsWith("token_")) {
      tokenId = subscriptionId;
    }

    // If tokenId is missing but we have customerId, query customer tokens on Razorpay
    if (tokenId == null && customerId != null) {
      try {
        JsonNode tokensJson = get("/customers/" + encode(customerId) + "/tokens");
        JsonNode items = tokensJson.path("items");
        if (items.isArray() && !items.isEmpty()) {
          tokenId = text(items.get(items.size() - 1), "id");
          log.info("Resolved token {} from Razorpay customer tokens for {}", tokenId, customerId);
        }
      } catch (Exception ex) {
        log.warn("Could not fetch customer tokens: {}", ex.getMessage());
      }
    }

    if (email == null || email.isBlank()) {
      email = "billing@unifydesk.in";
    }
    if (phone == null || phone.isBlank()) {
      phone = "9999999999";
    }

    // 2. Create Charge Order on Razorpay
    String rzpOrderId = null;
    try {
      Map<String, Object> orderBody = new LinkedHashMap<>();
      orderBody.put("amount", toPaise(amount));
      orderBody.put("currency", "INR");
      orderBody.put("receipt", sanitizeReceipt(paymentId));
      orderBody.put("payment_capture", 1);
      if (customerId != null) {
        orderBody.put("customer_id", customerId);
      }
      orderBody.put("notes", Map.of(
          "type", "WALLET_AUTO_RECHARGE",
          "internal_payment_id", paymentId
      ));

      JsonNode orderJson = post("/orders", orderBody);
      rzpOrderId = text(orderJson, "id");
    } catch (Exception orderEx) {
      log.error("Failed to create Razorpay charge order for payment {}: {}", paymentId, orderEx.getMessage());
      return new RemoteSubscriptionPayment(
          paymentId,
          null,
          "FAILED",
          amount,
          "INR",
          Instant.now(),
          null,
          "Order creation failed: " + orderEx.getMessage());
    }

    // 3. Trigger Recurring Payment against the saved token
    try {
      Map<String, Object> recurringBody = new LinkedHashMap<>();
      recurringBody.put("email", email);
      recurringBody.put("contact", phone);
      recurringBody.put("amount", toPaise(amount));
      recurringBody.put("currency", "INR");
      recurringBody.put("order_id", rzpOrderId);
      if (customerId != null) {
        recurringBody.put("customer_id", customerId);
      }
      if (tokenId != null) {
        recurringBody.put("token", tokenId);
      }
      recurringBody.put("recurring", "1");
      recurringBody.put("description", "UnifyDesk wallet reload");

      JsonNode chargeJson = post("/payments/create/recurring", recurringBody);
      String rzpPaymentId = text(chargeJson, "id");
      String status = text(chargeJson, "status");
      log.info("Razorpay recurring charge response for payment {}: id={}, status={}", paymentId, rzpPaymentId, status);

      if ("captured".equalsIgnoreCase(status) || "authorized".equalsIgnoreCase(status)) {
        return new RemoteSubscriptionPayment(
            paymentId,
            rzpPaymentId,
            "SUCCESS",
            amount,
            "INR",
            Instant.now(),
            null,
            null);
      } else {
        String errDesc = text(chargeJson.path("error"), "description");
        return new RemoteSubscriptionPayment(
            paymentId,
            rzpPaymentId,
            "FAILED",
            amount,
            "INR",
            Instant.now(),
            null,
            errDesc != null ? errDesc : "Payment status: " + status);
      }
    } catch (Exception chargeEx) {
      log.error("Razorpay recurring payment failed for payment {}: {}", paymentId, chargeEx.getMessage());
      return new RemoteSubscriptionPayment(
          paymentId,
          null,
          "FAILED",
          amount,
          "INR",
          Instant.now(),
          null,
          chargeEx.getMessage());
    }
  }

  private String getOrCreateRazorpayCustomer(Customer customer) {
    if (customer == null) {
      return null;
    }
    if (customer.id() != null && customer.id().startsWith("cust_")) {
      return customer.id();
    }
    try {
      Map<String, Object> body = new LinkedHashMap<>();
      if (customer.name() != null && !customer.name().isBlank()) {
        body.put("name", customer.name().trim());
      }
      if (customer.email() != null && !customer.email().isBlank()) {
        body.put("email", customer.email().trim());
      }
      if (customer.phone() != null && !customer.phone().isBlank()) {
        body.put("contact", customer.phone().trim());
      }
      body.put("notes", Map.of("internal_id", customer.id() != null ? customer.id() : ""));

      JsonNode json = post("/customers", body);
      String custId = text(json, "id");
      log.info("Created Razorpay customer {} for {}", custId, customer.email());
      return custId;
    } catch (Exception ex) {
      log.warn("Could not create customer in Razorpay: {}", ex.getMessage());
      return null;
    }
  }

  private String hmacSha256(String data, String key) {
    try {
      javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
      mac.init(new javax.crypto.spec.SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to calculate HMAC SHA256", e);
    }
  }

  @Override
  public RemoteSubscriptionPayment fetchSubscriptionPayment(
      String subscriptionId, String paymentId) {
    try {
      JsonNode p = get("/payments/" + encode(paymentId));
      return new RemoteSubscriptionPayment(
          paymentId,
          text(p, "id"),
          mapPaymentStatus(text(p, "status")),
          fromPaise(p.path("amount").asLong(0)),
          text(p, "currency"),
          instantFromEpochSeconds(p.path("created_at").asLong(0)),
          null,
          null);
    } catch (Exception ex) {
      return new RemoteSubscriptionPayment(
          paymentId, null, "PENDING", BigDecimal.ZERO, "INR", Instant.now(), null, null);
    }
  }

  private String ensurePlanExists(BigDecimal maxAmount, String currency) {
    String curr = currency != null && !currency.isBlank() ? currency.toUpperCase() : "INR";
    int amountVal = maxAmount != null ? maxAmount.intValue() : 15000;
    String planKey = "PLAN_MANDATE_" + amountVal + "_" + curr;
    if (verifiedPlans.contains(planKey)) {
      return planKey;
    }

    try {
      Map<String, Object> planBody = new LinkedHashMap<>();
      planBody.put("period", "monthly");
      planBody.put("interval", 1);
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("name", "UnifyDesk AutoPay " + curr + " " + amountVal);
      item.put("amount", toPaise(maxAmount != null ? maxAmount : new BigDecimal("15000")));
      item.put("currency", curr);
      item.put("description", "UnifyDesk 20-year recurring mandate up to 15k");
      planBody.put("item", item);

      JsonNode json = post("/plans", planBody);
      String createdPlanId = text(json, "id");
      if (createdPlanId != null) {
        verifiedPlans.add(createdPlanId);
        return createdPlanId;
      }
    } catch (Exception e) {
      log.warn("Plan creation in Razorpay note: {}", e.getMessage());
    }
    verifiedPlans.add(planKey);
    return planKey;
  }

  private long toPaise(BigDecimal amount) {
    if (amount == null) return 0L;
    return amount.multiply(new BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).longValue();
  }

  private BigDecimal fromPaise(long paise) {
    return new BigDecimal(paise).divide(new BigDecimal(100), 2, RoundingMode.HALF_UP);
  }

  private String mapPaymentStatus(String rzpStatus) {
    if (rzpStatus == null) return "PENDING";
    return switch (rzpStatus.toLowerCase()) {
      case "captured" -> "SUCCESS";
      case "failed" -> "FAILED";
      case "authorized" -> "PENDING";
      default -> rzpStatus.toUpperCase();
    };
  }

  private String mapRefundStatus(String rzpStatus) {
    if (rzpStatus == null) return "PENDING";
    return switch (rzpStatus.toLowerCase()) {
      case "processed" -> "SUCCESS";
      case "failed" -> "FAILED";
      default -> rzpStatus.toUpperCase();
    };
  }

  private JsonNode post(String path, Object body) {
    assertConfigured();
    try {
      String response =
          client()
              .post()
              .uri(path)
              .contentType(MediaType.APPLICATION_JSON)
              .header("Authorization", basicAuthHeader())
              .body(body)
              .retrieve()
              .body(String.class);
      return read(response);
    } catch (RestClientResponseException ex) {
      String responseBody = ex.getResponseBodyAsString();
      if (path.startsWith("/subscriptions") || path.startsWith("/plans")) {
        log.warn("Razorpay API POST {} note: status={}, response={}", path, ex.getStatusCode(), responseBody);
      } else {
        log.error("Razorpay API POST {} failed: status={}, response={}", path, ex.getStatusCode(), responseBody);
      }
      String detail = null;
      try {
        JsonNode errJson = objectMapper.readTree(responseBody);
        JsonNode err = errJson.path("error");
        if (err.hasNonNull("description")) {
          detail = err.path("description").asText();
        }
      } catch (Exception ignored) {
      }
      String msg = detail != null && !detail.isBlank()
          ? "Razorpay rejected the request: " + detail
          : "Razorpay payment request failed. Please retry or check provider configuration.";
      throw new ServiceUnavailableException(msg, ex);
    }
  }

  private JsonNode get(String path) {
    assertConfigured();
    try {
      String response =
          client()
              .get()
              .uri(path)
              .header("Authorization", basicAuthHeader())
              .retrieve()
              .body(String.class);
      return read(response);
    } catch (RestClientResponseException ex) {
      log.debug("Razorpay API GET {} returned status={}", path, ex.getStatusCode());
      throw ex;
    }
  }

  private RestClient client() {
    return RestClient.builder().baseUrl(properties.getBaseUrl()).build();
  }

  private String basicAuthHeader() {
    String auth = properties.getKeyId() + ":" + properties.getKeySecret();
    return "Basic " + Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
  }

  private void assertRecurringEnabled() {
    assertConfigured();
    if (!properties.isRecurringEnabled()) {
      throw new ConflictException("Recurring payments are disabled pending provider enablement");
    }
  }

  private void assertConfigured() {
    if (!properties.isEnabled()
        || properties.getKeyId() == null
        || properties.getKeyId().isBlank()
        || properties.getKeySecret() == null
        || properties.getKeySecret().isBlank()) {
      throw new ConflictException("Razorpay provider is not configured");
    }
  }

  private JsonNode read(String value) {
    try {
      return objectMapper.readTree(value == null ? "{}" : value);
    } catch (Exception exception) {
      throw new IllegalStateException("Razorpay returned an invalid JSON response", exception);
    }
  }

  private String text(JsonNode node, String field) {
    String val = node.path(field).asText(null);
    return val == null || val.isBlank() ? null : val;
  }

  private Instant instantFromEpochSeconds(long seconds) {
    if (seconds <= 0) return Instant.now();
    return Instant.ofEpochSecond(seconds);
  }

  private String encode(String value) {
    return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private String sanitizeReceipt(String orderId) {
    if (orderId == null) return "RCPT";
    String clean = orderId.replaceAll("[^a-zA-Z0-9_-]", "");
    return clean.length() > 36 ? clean.substring(clean.length() - 36) : clean;
  }
}
