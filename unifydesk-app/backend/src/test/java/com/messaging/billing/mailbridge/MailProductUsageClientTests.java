package com.messaging.billing.mailbridge;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.common.exception.ServiceUnavailableException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MailProductUsageClientTests {
  private HttpClient http;
  private HttpResponse<String> response;
  private MailProductUsageClient client;

  @SuppressWarnings("unchecked")
  @BeforeEach
  void setUp() throws Exception {
    http = mock(HttpClient.class);
    response = mock(HttpResponse.class);
    client = new MailProductUsageClient(http,
        "http://mail-backend.email.svc.cluster.local:8010",
        "0123456789abcdef0123456789abcdef", new ObjectMapper());
    when(response.statusCode()).thenReturn(200);
    when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(response);
  }

  @Test
  void parsesLosslessStringIdsAndRemotePricingWithoutCentralBilling() {
    when(response.body()).thenReturn("""
        {"status":true,"data":{"projectId":"123",
          "entries":[
            {"usageId":"9007199254740993","recipientId":"9007199254740994",
             "messageId":"9007199254740995","occurredAt":"2026-10-09T12:00:00Z",
             "productPriceMicroInr":12000}],
          "nextAfterId":"9007199254740993","hasMore":false}}
        """);

    var page = client.handoffs(123L, 9007199254740992L, 50);
    assertEquals(1, page.entries().size());
    assertEquals(9007199254740993L, page.entries().getFirst().id());
    assertEquals(12000L, page.entries().getFirst().productPriceMicroInr());
    assertFalse(page.hasMore());
  }

  @Test
  void refusesCrossProjectResponse() {
    when(response.body()).thenReturn("""
        {"status":true,"data":{"projectId":"999","entries":[],
          "nextAfterId":"0","hasMore":false}}
        """);
    assertThrows(ServiceUnavailableException.class, () -> client.handoffs(123L, 0L, 10));
  }

  @Test
  void refusesRepeatedOrReorderedUsageIds() {
    when(response.body()).thenReturn("""
        {"status":true,"data":{"projectId":"123","entries":[
          {"usageId":"10","recipientId":"11","messageId":"12",
           "occurredAt":"2026-10-09T12:00:00Z","productPriceMicroInr":0},
          {"usageId":"10","recipientId":"13","messageId":"14",
           "occurredAt":"2026-10-09T12:00:00Z","productPriceMicroInr":0}],
          "nextAfterId":"10","hasMore":false}}
        """);
    assertThrows(ServiceUnavailableException.class, () -> client.handoffs(123L, 0L, 10));
  }

  @Test
  void refusesIncorrectPageCursor() {
    when(response.body()).thenReturn("""
        {"status":true,"data":{"projectId":"123","entries":[
          {"usageId":"11","recipientId":"12","messageId":"13",
           "occurredAt":"2026-10-09T12:00:00Z","productPriceMicroInr":12000}],
          "nextAfterId":"12","hasMore":true}}
        """);
    assertThrows(ServiceUnavailableException.class, () -> client.handoffs(123L, 10L, 10));
  }

  @Test
  void rejectsExternalUnencryptedHttpBeforeAnyNetworkCall() {
    var unsafe = new MailProductUsageClient(http,
        "http://example.com", "0123456789abcdef0123456789abcdef",
        new ObjectMapper());
    assertThrows(ServiceUnavailableException.class, () -> unsafe.head(1L));
    verifyNoMoreInteractions(http);
  }

  @Test
  void missingInternalTokenFailsClosed() {
    var noToken = new MailProductUsageClient(http,
        "https://mail.example.com", "", new ObjectMapper());
    assertThrows(ServiceUnavailableException.class, () -> noToken.head(1L));
    verifyNoMoreInteractions(http);
  }

  @Test
  void fetchesCurrentHeadWithoutAssumingProjectEqualsCompanyId() {
    when(response.body()).thenReturn("{\"status\":true,\"data\":\"9007199254740993\"}");
    assertEquals(9007199254740993L, client.head(123L));
  }
}
