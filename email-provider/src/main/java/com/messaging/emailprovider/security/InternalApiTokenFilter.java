package com.messaging.emailprovider.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Protects delivery-plane control APIs from direct client traffic. Only the trusted UnifyDesk
 * backend should possess the bearer token. Keep Postfix feedback endpoints on a private network.
 */
@Component
public class InternalApiTokenFilter extends OncePerRequestFilter {
  private final byte[] expected;

  public InternalApiTokenFilter(@Value("${app.security.api.internal-token:}") String token) {
    this.expected = token == null ? new byte[0] : token.getBytes(StandardCharsets.UTF_8);
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    // The HTML login shell is public; authenticated console APIs remain protected
    // by ConsoleAuthFilter under /console/v1/. Limit this bypass to static GET/HEAD.
    boolean publicConsolePage = path.equals("/console/index.html")
        && ("GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod()));
    return publicConsolePage
        || path.startsWith("/console/v1/")
        || path.startsWith("/v1/")
        || path.equals("/api/v1/health")
        || path.equals("/actuator/health")
        || path.startsWith("/actuator/health/")
        || path.equals("/error")
        || path.equals("/internal/v1/feedback")
        || path.startsWith("/internal/v1/feedback/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (expected.length < 32) {
      response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
          "Email provider internal API is not configured");
      return;
    }
    String authorization = request.getHeader("Authorization");
    if (authorization == null || !authorization.startsWith("Bearer ")) {
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
      return;
    }
    byte[] actual = authorization.substring(7).getBytes(StandardCharsets.UTF_8);
    if (!MessageDigest.isEqual(expected, actual)) {
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
      return;
    }
    chain.doFilter(request, response);
  }
}
