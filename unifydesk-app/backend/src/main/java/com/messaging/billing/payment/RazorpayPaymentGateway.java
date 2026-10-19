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
      throw new ServiceUnavailableException("Could not verify Razorpay order payments", e);
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
  public RecurringAuthorization fetchRecurringAuthorization(String tokenReference) {
    assertRecurringEnabled();
    // Paid authorization orders are never sufficient evidence of a valid recurring token.
    if (tokenReference == null || !tokenReference.startsWith("token_")) {
      return new RecurringAuthorization(tokenReference, tokenReference, "PENDING_AUTH", null);
    }
    String customerId = jdbcTemplate.query(
        "select payment_method_type from payment_provider_authorizations "
            + "where provider = 'RAZORPAY' and provider_subscription_id = ? "
            + "and authorization_status = 'ACTIVE' limit 1",
        rs -> rs.next() ? rs.getString(1) : null, tokenReference);
    if (customerId == null || !customerId.startsWith("cust_")) {
      return new RecurringAuthorization(tokenReference, tokenReference, "PENDING_AUTH", null);
    }
    JsonNode tokens = get("/customers/" + encode(customerId) + "/tokens").path("items");
    if (tokens.isArray()) {
      for (JsonNode token : tokens) {
        if (tokenReference.equals(text(token, "id"))) {
          boolean confirmed = token.path("recurring").asBoolean(false)
              && "confirmed".equalsIgnoreCase(text(token.path("recurring_details"), "status"));
          return new RecurringAuthorization(tokenReference, tokenReference,
              confirmed ? "ACTIVE" : "PENDING_AUTH", customerId);
        }
      }
    }
    return new RecurringAuthorization(tokenReference, tokenReference, "PENDING_AUTH", customerId);
  }

  @Override
  public String verifyMandateToken(
      String orderId, String paymentId, String signature, String customerId) {
    assertRecurringEnabled();
    if (orderId == null || !orderId.startsWith("order_")
        || paymentId == null || !paymentId.startsWith("pay_")
        || customerId == null || !customerId.startsWith("cust_")
        || signature == null || signature.isBlank()) {
      throw new ConflictException("Incomplete recurring mandate checkout evidence");
    }
    String expected = hmacSha256(orderId + "|" + paymentId, properties.getKeySecret());
    if (!java.security.MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.US_ASCII),
        signature.toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.US_ASCII))) {
      throw new ConflictException("Invalid recurring mandate checkout signature");
    }
    JsonNode payment = get("/payments/" + encode(paymentId));
    if (!orderId.equals(text(payment, "order_id"))
        || !customerId.equals(text(payment, "customer_id"))
        || payment.path("amount").asLong(-1) != 100
        || !"INR".equalsIgnoreCase(text(payment, "currency"))
        || !"captured".equalsIgnoreCase(text(payment, "status"))
        || !"upi".equalsIgnoreCase(text(payment, "method"))) {
      throw new ConflictException("Mandate payment does not match the expected captured UPI authorization");
    }
    String tokenId = text(payment, "token_id");
    if (tokenId == null || !tokenId.startsWith("token_")) {
      throw new ConflictException("The authorization payment has no recurring token");
    }
    JsonNode tokens = get("/customers/" + encode(customerId) + "/tokens").path("items");
    if (tokens.isArray()) {
      for (JsonNode token : tokens) {
        if (tokenId.equals(text(token, "id"))
            && token.path("recurring").asBoolean(false)
            && "confirmed".equalsIgnoreCase(text(token.path("recurring_details"), "status"))) {
          return tokenId;
        }
      }
    }
    throw new ConflictException("Razorpay has not confirmed this recurring token");
  }

  @Override
  public void cancelRecurringAuthorization(String tokenId, String customerId) {
    // Cancellation must remain available even when new recurring debits are disabled.
    assertConfigured();
    if (tokenId == null || !tokenId.startsWith("token_")
        || customerId == null || !customerId.startsWith("cust_")) {
      throw new ConflictException("Valid Razorpay token and customer are required");
    }
    try {
      client().delete()
          .uri("/customers/" + encode(customerId) + "/tokens/" + encode(tokenId))
          .header("Authorization", basicAuthHeader())
          .retrieve().toBodilessEntity();
    } catch (RestClientResponseException ex) {
      if (ex.getStatusCode().value() != 404) throw ex;
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
      String tokenReference, String commandId, BigDecimal amount,
      Instant scheduledAt, String idempotencyKey) {
    assertRecurringEnabled();
    if (tokenReference == null || !tokenReference.startsWith("token_")
        || commandId == null || commandId.isBlank()
        || amount == null || amount.signum() <= 0
        || amount.compareTo(new BigDecimal("15000")) > 0) {
      throw new ConflictException("Invalid recurring debit command, token, or amount");
    }
    // Commit a unique local command BEFORE contacting Razorpay. Any crash or timeout
    // before binding the remote order is ambiguous, never an invitation to debit again.
    int claimed = jdbcTemplate.update(
        "insert into razorpay_recurring_charge_intents "
            + "(command_id, provider_token_id, amount, currency) "
            + "values (?, ?, ?, 'INR') on conflict (command_id) do nothing",
        commandId, tokenReference, amount);
    if (claimed == 0) {
      return fetchSubscriptionPayment(tokenReference, commandId);
    }
    List<Map<String, Object>> rows = jdbcTemplate.queryForList(
        """
        select a.payment_method_type, cbp.billing_email, cbp.billing_phone,
               c.business_email
          from payment_provider_authorizations a
          left join company_billing_profiles cbp on cbp.company_id = a.company_id
          left join companies c on c.id = a.company_id
         where a.provider = 'RAZORPAY'
           and a.authorization_status = 'ACTIVE'
           and a.provider_subscription_id = ?
         limit 1
        """,
        tokenReference);
    if (rows.size() != 1) {
      throw new ConflictException("No active Razorpay mandate matches the requested debit");
    }
    Map<String, Object> row = rows.getFirst();
    String customerId = (String) row.get("payment_method_type");
    String email = (String) row.get("billing_email");
    if (email == null || email.isBlank()) email = (String) row.get("business_email");
    String phone = (String) row.get("billing_phone");
    if (customerId == null || !customerId.startsWith("cust_")
        || email == null || email.isBlank() || phone == null || phone.isBlank()) {
      throw new ConflictException("Mandate customer and actual billing contact are required");
    }
    if (!"ACTIVE".equals(fetchRecurringAuthorization(tokenReference).authorizationStatus())) {
      throw new ConflictException("Razorpay has not confirmed the mandate token");
    }

    Map<String, Object> orderRequest = new LinkedHashMap<>();
    orderRequest.put("amount", toPaise(amount));
    orderRequest.put("currency", "INR");
    orderRequest.put("receipt", sanitizeReceipt(commandId));
    orderRequest.put("payment_capture", true);
    orderRequest.put("customer_id", customerId);
    orderRequest.put("notes", Map.of("type", "RECURRING_DEBIT", "command_id", commandId));
    String providerOrderId = text(post("/orders", orderRequest), "id");
    if (providerOrderId == null || !providerOrderId.startsWith("order_")) {
      throw new ServiceUnavailableException("Razorpay did not create a recurring charge order");
    }
    int bound = jdbcTemplate.update(
        "update razorpay_recurring_charge_intents "
            + "set provider_order_id=?, updated_at=now() "
            + "where command_id=? and provider_order_id is null",
        providerOrderId, commandId);
    if (bound != 1) throw new ConflictException("Recurring charge order binding conflicted");

    try {
      Map<String, Object> charge = new LinkedHashMap<>();
      charge.put("email", email);
      charge.put("contact", phone);
      charge.put("amount", toPaise(amount));
      charge.put("currency", "INR");
      charge.put("order_id", providerOrderId);
      charge.put("customer_id", customerId);
      charge.put("token", tokenReference);
      charge.put("recurring", true);
      charge.put("description", "UnifyDesk authorized recurring payment");
      JsonNode response = post("/payments/create/recurring", charge);
      String providerPaymentId = text(response, "id");
      if (providerPaymentId != null) {
        jdbcTemplate.update(
            "update razorpay_recurring_charge_intents "
                + "set provider_payment_id = ?, updated_at = now() where command_id = ?",
            providerPaymentId, commandId);
      }
      String status = text(response, "status");
      if ("captured".equalsIgnoreCase(status)
          && (response.path("amount").asLong(-1) != toPaise(amount)
              || !providerOrderId.equals(text(response, "order_id")))) {
        throw new ConflictException("Recurring provider capture does not match its order");
      }
      return new RemoteSubscriptionPayment(
          commandId, providerPaymentId, mapPaymentStatus(status), amount, "INR",
          Instant.now(), null, text(response, "error_code"));
    } catch (RuntimeException ex) {
      // A timeout after provider submission is ambiguous. Poll the persisted provider
      // order for capture; never create a new order or debit for the same command.
      log.warn("Recurring debit {} has an uncertain provider outcome", commandId);
      return new RemoteSubscriptionPayment(
          commandId, null, "PENDING", amount, "INR", Instant.now(), null,
          "PROVIDER_OUTCOME_UNCERTAIN");
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
      body.put("fail_existing", "0");
      body.put("notes", Map.of("internal_id", customer.id() != null ? customer.id() : ""));

      JsonNode json = post("/customers", body);
      String custId = text(json, "id");
      log.info("Created Razorpay customer {} for {}", custId, customer.email());
      return custId;
    } catch (Exception ex) {
      throw new ServiceUnavailableException("Unable to register Razorpay billing customer", ex);
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
      String tokenReference, String commandId) {
    // Authorization checkout commands do not represent collected money.
    if (commandId != null && commandId.startsWith("UDA-")) {
      return new RemoteSubscriptionPayment(
          commandId, null, "PENDING", BigDecimal.ONE, "INR", Instant.now(), null, null);
    }
    List<Map<String, Object>> rows = jdbcTemplate.queryForList(
        "select provider_order_id, provider_token_id, amount, currency, provider_payment_id "
            + "from razorpay_recurring_charge_intents where command_id = ?", commandId);
    if (rows.size() != 1) {
      throw new ConflictException("No provider order has been persisted for this recurring command");
    }
    Map<String, Object> row = rows.getFirst();
    if (!tokenReference.equals(row.get("provider_token_id"))) {
      throw new ConflictException("Recurring command is bound to another provider token");
    }
    String providerOrderId = (String) row.get("provider_order_id");
    if (providerOrderId == null || !providerOrderId.startsWith("order_")) {
      throw new ConflictException(
          "Recurring order creation is ambiguous; provider reconciliation is required");
    }
    BigDecimal expectedAmount = (BigDecimal) row.get("amount");
    String expectedCurrency = (String) row.get("currency");
    String knownPaymentId = (String) row.get("provider_payment_id");
    List<RemotePayment> payments = fetchOrderPayments(providerOrderId);
    List<RemotePayment> captured = payments.stream()
        .filter(p -> "SUCCESS".equalsIgnoreCase(p.status())).toList();
    if (captured.size() > 1) {
      throw new ConflictException("Multiple captured payments require financial reconciliation");
    }
    if (captured.size() == 1) {
      RemotePayment p = captured.getFirst();
      if (p.providerPaymentId() == null
          || (knownPaymentId != null && !knownPaymentId.equals(p.providerPaymentId()))
          || p.amount().compareTo(expectedAmount) != 0
          || !expectedCurrency.equalsIgnoreCase(p.currency())
          || !"paid".equalsIgnoreCase(fetchOrder(providerOrderId).status())) {
        throw new ConflictException("Captured recurring payment does not match canonical order");
      }
      jdbcTemplate.update(
          "update razorpay_recurring_charge_intents "
              + "set provider_payment_id=?, updated_at=now() where command_id=?",
          p.providerPaymentId(), commandId);
      return new RemoteSubscriptionPayment(
          commandId, p.providerPaymentId(), "SUCCESS", p.amount(), p.currency(),
          p.providerCreatedAt(), null, null);
    }
    boolean failed = payments.stream().anyMatch(p -> "FAILED".equalsIgnoreCase(p.status()));
    return new RemoteSubscriptionPayment(
        commandId, knownPaymentId, failed ? "FAILED" : "PENDING", expectedAmount,
        expectedCurrency, Instant.now(), null, failed ? "PROVIDER_DECLINED" : null);
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
