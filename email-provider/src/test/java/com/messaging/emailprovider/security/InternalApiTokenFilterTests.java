package com.messaging.emailprovider.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class InternalApiTokenFilterTests {
  private static final String TOKEN = "fixed-test-token-with-at-least-32-characters";

  @Test
  void deniesUnconfiguredApi() throws ServletException, IOException {
    assertEquals(503, call(new InternalApiTokenFilter(""), "/api/v1/companies/1/emails", TOKEN));
  }

  @Test
  void deniesMissingAndWrongToken() throws ServletException, IOException {
    var filter = new InternalApiTokenFilter(TOKEN);
    assertEquals(401, call(filter, "/api/v1/companies/1/emails", null));
    assertEquals(401, call(filter, "/api/v1/companies/1/emails", "wrong"));
  }

  @Test
  void acceptsTrustedServiceAndKeepsHealthOpen() throws ServletException, IOException {
    var filter = new InternalApiTokenFilter(TOKEN);
    assertEquals(200, call(filter, "/api/v1/companies/1/emails", TOKEN));
    assertEquals(200, call(filter, "/actuator/health/readiness", null));
  }

  private int call(InternalApiTokenFilter filter, String uri, String token)
      throws ServletException, IOException {
    var request = new MockHttpServletRequest("GET", uri);
    if (token != null) request.addHeader("Authorization", "Bearer " + token);
    var response = new MockHttpServletResponse();
    filter.doFilter(request, response, new MockFilterChain());
    return response.getStatus();
  }
}
