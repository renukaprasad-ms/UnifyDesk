package com.messaging.billing.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

class RazorpayRecurringDebitTests {
  private final ObjectMapper mapper = new ObjectMapper();
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final List<String> paths = new CopyOnWriteArrayList<>();
  private HttpServer server;
  private RazorpayPaymentGateway gateway;
  private int debitStatus = 200;
  private String debitResponse = "{\"razorpay_payment_id\":\"pay_recurring\"}";
  private volatile JsonNode debitRequest;
  private volatile JsonNode orderRequest;
  private String tokenResponse =
      "{\"items\":[{\"id\":\"token_mandate\",\"recurring\":true,\"recurring_details\":{\"status\":\"confirmed\"}}]}";
  private String authorizationPayment;

  @BeforeEach
  void setup() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          String path = exchange.getRequestURI().getPath();
          paths.add(exchange.getRequestMethod() + " " + path);
          String response;
          int status = 200;
          if (path.equals("/v1/customers/cust_customer/tokens")) {
            response = tokenResponse;
          } else if (path.equals("/v1/customers")) {
            response = "{\"id\":\"cust_customer\"}";
          } else if (path.equals("/v1/payments/pay_authorization")) {
            response = authorizationPayment;
          } else if (path.equals("/v1/orders")) {
            orderRequest = mapper.readTree(exchange.getRequestBody());
            response = "{\"id\":\"order_debit\"}";
          } else if (path.equals("/v1/payments/create/recurring")) {
            debitRequest = mapper.readTree(exchange.getRequestBody());
            response = debitResponse;
            status = debitStatus;
          } else {
            response = "{}";
            status = 404;
          }
          byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(status, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
    RazorpayProperties properties = new RazorpayProperties();
    properties.setRecurringEnabled(true);
    properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
    gateway = new RazorpayPaymentGateway(properties, mapper, jdbc);
    when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
    when(jdbc.queryForList(contains("select a.payment_method_type"), any(Object[].class)))
        .thenReturn(
            List.of(
                Map.of(
                    "payment_method_type",
                    "cust_customer",
                    "billing_email",
                    "billing@example.com",
                    "billing_phone",
                    "9876543210")));
    doReturn("cust_customer")
        .when(jdbc)
        .query(anyString(), any(ResultSetExtractor.class), any(Object[].class));
  }

  @AfterEach
  void stop() {
    if (server != null) server.stop(0);
  }

  @ParameterizedTest
  @ValueSource(strings = {"card", "emandate", "upi"})
  void createsAuthorizationOrderForSelectedMethod(String method) {
    var result =
        gateway.createRecurringAuthorization(
            "UDS-AP-test",
            new BigDecimal("15000"),
            "INR",
            new PaymentProviderGateway.Customer(
                "company-test", "Acme", "billing@example.com", "9876543210", method),
            "https://example.com",
            "test-idempotency");

    assertThat(result.authorizationStatus()).isEqualTo("PENDING_AUTH");
    assertThat(orderRequest.path("method").asText()).isEqualTo(method);
    assertThat(orderRequest.path("amount").asLong()).isEqualTo(method.equals("emandate") ? 0 : 100);
    assertThat(orderRequest.path("token").path("max_amount").asLong()).isEqualTo(1500000);
    if (!method.equals("emandate")) {
      assertThat(orderRequest.path("token").path("frequency").asText()).isEqualTo("as_presented");
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"card", "emandate", "upi"})
  void verifiesMethodSpecificAuthorizationAndConfirmedToken(String method) throws Exception {
    authorizationPayment =
        mapper.writeValueAsString(
            Map.of(
                "order_id",
                "order_debit",
                "customer_id",
                "cust_customer",
                "amount",
                method.equals("emandate") ? 0 : 100,
                "currency",
                "INR",
                "status",
                "captured",
                "method",
                method,
                "token_id",
                "token_mandate"));

    assertThat(
            gateway.verifyMandateToken(
                "order_debit", "pay_authorization", signature(), "cust_customer", method))
        .isEqualTo("token_mandate");
    assertThat(
            gateway.verifyMandateToken(
                "order_debit", "pay_authorization", signature(), "cust_customer", "auto"))
        .isEqualTo("token_mandate");
  }

  @Test
  void automaticCheckoutDoesNotRestrictOrderToOnePaymentMethod() {
    gateway.createRecurringAuthorization(
        "UDS-AP-test",
        new BigDecimal("15000"),
        "INR",
        new PaymentProviderGateway.Customer(
            "company-test", "Acme", "billing@example.com", "9876543210", "auto"),
        "https://example.com",
        "test-idempotency");

    assertThat(orderRequest.has("method")).isFalse();
    assertThat(orderRequest.path("token").path("max_amount").asLong()).isEqualTo(1500000L);
  }

  @Test
  void bankTokenAwaitingConfirmationCanBeBoundWithoutBeingConsideredActive() throws Exception {
    authorizationPayment =
        mapper.writeValueAsString(
            Map.of(
                "order_id",
                "order_debit",
                "customer_id",
                "cust_customer",
                "amount",
                0,
                "currency",
                "INR",
                "status",
                "authorized",
                "method",
                "emandate",
                "token_id",
                "token_mandate"));
    tokenResponse =
        "{\"items\":[{\"id\":\"token_mandate\",\"method\":\"emandate\",\"recurring\":true,\"recurring_details\":{\"status\":\"initiated\"}}]}";

    assertThat(
            gateway.verifyMandateToken(
                "order_debit", "pay_authorization", signature(), "cust_customer", "emandate"))
        .isEqualTo("token_mandate");
    assertThat(gateway.fetchRecurringAuthorization("token_mandate").authorizationStatus())
        .isEqualTo("PENDING_AUTH");
  }

  @Test
  void upiAuthorizationCannotBeVerifiedAsCard() throws Exception {
    authorizationPayment =
        mapper.writeValueAsString(
            Map.of(
                "order_id",
                "order_debit",
                "customer_id",
                "cust_customer",
                "amount",
                100,
                "currency",
                "INR",
                "status",
                "captured",
                "method",
                "upi",
                "token_id",
                "token_mandate"));
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                gateway.verifyMandateToken(
                    "order_debit", "pay_authorization", signature(), "cust_customer", "card"))
        .isInstanceOf(com.messaging.common.exception.ConflictException.class);
  }

  private String signature() throws Exception {
    javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
    mac.init(
        new javax.crypto.spec.SecretKeySpec(
            "rzp_test_secret_placeholder".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return java.util.HexFormat.of()
        .formatHex(mac.doFinal("order_debit|pay_authorization".getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void documentedResponseBindsPaymentIdAndKeepsDebitPendingUntilVerified() {
    var result = charge();

    assertThat(result.providerPaymentId()).isEqualTo("pay_recurring");
    assertThat(result.status()).isEqualTo("PENDING");
    assertThat(debitRequest.path("token").asText()).isEqualTo("token_mandate");
    assertThat(debitRequest.path("order_id").asText()).isEqualTo("order_debit");
    assertThat(debitRequest.path("amount").asLong()).isEqualTo(200000L);
    assertThat(debitRequest.path("recurring").asBoolean()).isTrue();
    assertThat(paths)
        .containsExactly(
            "GET /v1/customers/cust_customer/tokens",
            "POST /v1/orders",
            "POST /v1/payments/create/recurring");
    ArgumentCaptor<Object[]> values = ArgumentCaptor.forClass(Object[].class);
    verify(jdbc).update(contains("set provider_payment_id = ?"), values.capture());
    assertThat(values.getValue()).containsExactly("pay_recurring", "WTR-test");
  }

  @Test
  void unavailableRecurringApiIsDiagnosedWithoutResubmittingOrClaimingSuccess() {
    debitStatus = 400;
    debitResponse =
        "{\"error\":{\"code\":\"BAD_REQUEST_ERROR\",\"description\":\"The requested URL was not found on the server.\"}}";

    var result = charge();

    assertThat(result.status()).isEqualTo("PENDING");
    assertThat(result.failureCode()).isEqualTo("PROVIDER_RECURRING_API_UNAVAILABLE");
    assertThat(paths.stream().filter(path -> path.endsWith("/payments/create/recurring")))
        .hasSize(1);
    verify(jdbc, never()).update(contains("set provider_payment_id = ?"), any(Object[].class));
  }

  @Test
  void malformedSuccessResponseRemainsUnresolved() {
    debitResponse = "{}";

    var result = charge();

    assertThat(result.status()).isEqualTo("PENDING");
    assertThat(result.failureCode()).isEqualTo("PROVIDER_OUTCOME_UNCERTAIN");
    verify(jdbc, never()).update(contains("set provider_payment_id = ?"), any(Object[].class));
  }

  private PaymentProviderGateway.RemoteSubscriptionPayment charge() {
    return gateway.chargeAuthorizedSubscription(
        "token_mandate", "WTR-test", new BigDecimal("2000.00"), Instant.now(), "test-idempotency");
  }
}
