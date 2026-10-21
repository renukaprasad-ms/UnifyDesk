package com.messaging.emailprovider.product.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class EmailProductKeyFilterTests {
  private final EmailProjectApiKeyService auth = mock(EmailProjectApiKeyService.class);
  private final EmailProductKeyFilter filter = new EmailProductKeyFilter(auth);

  @Test
  void rejectsUnknownKey() throws ServletException, IOException {
    assertEquals(401, status("POST", "/v1/email-batches"));
  }

  @Test
  void requiresSendingEnabledForPost() throws ServletException, IOException {
    when(auth.authenticate(any(), eq(ApiKeyScope.SEND)))
        .thenReturn(new AuthenticatedProject(1L, 2L, 3L, false));
    assertEquals(403, status("POST", "/v1/email-batches"));
  }

  @Test
  void domainRoutesUseDomainScope() throws ServletException, IOException {
    when(auth.authenticate(any(), eq(ApiKeyScope.DOMAINS)))
        .thenReturn(new AuthenticatedProject(1L, 2L, 3L, false));
    assertEquals(200, status("POST", "/v1/domains/10/verify"));
    verify(auth).authenticate(any(), eq(ApiKeyScope.DOMAINS));
  }

  private int status(String method, String url) throws ServletException, IOException {
    MockHttpServletRequest request = new MockHttpServletRequest(method, url);
    request.addHeader("Authorization", "Bearer test");
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request, response, new MockFilterChain());
    return response.getStatus();
  }
}
