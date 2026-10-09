package com.messaging.emailprovider.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@Component
public class RequestObservabilityFilter extends OncePerRequestFilter {

  public static final String REQUEST_ID_HEADER = "X-Request-ID";
  private static final String MDC_REQUEST_ID = "requestId";
  private static final int MAX_REQUEST_ID_LENGTH = 128;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String requestId = resolveRequestId(request.getHeader(REQUEST_ID_HEADER));
    long startedAt = System.nanoTime();
    MDC.put(MDC_REQUEST_ID, requestId);
    response.setHeader(REQUEST_ID_HEADER, requestId);

    try {
      filterChain.doFilter(request, response);
    } finally {
      long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
      log.info(
          "http_request method={} path={} status={} durationMs={}",
          request.getMethod(),
          request.getRequestURI(),
          response.getStatus(),
          durationMs);
      MDC.remove(MDC_REQUEST_ID);
    }
  }

  private String resolveRequestId(String candidate) {
    if (candidate == null || candidate.isBlank() || candidate.length() > MAX_REQUEST_ID_LENGTH) {
      return UUID.randomUUID().toString();
    }
    return candidate.replaceAll("[\\r\\n]", "");
  }
}
