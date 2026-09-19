package com.messaging.security.filter;

import com.messaging.auth.service.AuthCookieService;
import com.messaging.security.jwt.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final AuthCookieService authCookieService;
  private final JwtService jwtService;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    boolean realtimeRequest = isRealtimeRequest(request);
    if (SecurityContextHolder.getContext().getAuthentication() != null) {
      if (realtimeRequest) {
        log.info(
            "WS_DIAG auth_filter_existing uri={} principal={} upgrade={} connection={} origin={}",
            request.getRequestURI(),
            SecurityContextHolder.getContext().getAuthentication().getName(),
            header(request, "Upgrade"),
            header(request, "Connection"),
            header(request, "Origin"));
      }
      filterChain.doFilter(request, response);
      if (realtimeRequest) {
        log.info(
            "WS_DIAG auth_filter_exit uri={} status={} authenticated=true",
            request.getRequestURI(),
            response.getStatus());
      }
      return;
    }

    Optional<String> accessToken = authCookieService.accessToken(request);
    Optional<Claims> claims = accessToken.flatMap(jwtService::parseAccessToken);
    claims.ifPresent(parsedClaims -> authenticate(request, parsedClaims));

    if (realtimeRequest) {
      var authentication = SecurityContextHolder.getContext().getAuthentication();
      log.info(
          "WS_DIAG auth_filter uri={} accessCookiePresent={} tokenParsed={} authenticated={} principal={} upgrade={} connection={} origin={} forwardedProto={} forwardedHost={} remote={}",
          request.getRequestURI(),
          accessToken.isPresent(),
          claims.isPresent(),
          authentication != null,
          authentication == null ? "none" : authentication.getName(),
          header(request, "Upgrade"),
          header(request, "Connection"),
          header(request, "Origin"),
          header(request, "X-Forwarded-Proto"),
          header(request, "X-Forwarded-Host"),
          request.getRemoteAddr());
    }

    filterChain.doFilter(request, response);

    if (realtimeRequest) {
      log.info(
          "WS_DIAG auth_filter_exit uri={} status={} authenticated={}",
          request.getRequestURI(),
          response.getStatus(),
          SecurityContextHolder.getContext().getAuthentication() != null);
    }
  }

  private boolean isRealtimeRequest(HttpServletRequest request) {
    return request.getRequestURI() != null
        && request.getRequestURI().startsWith("/api/v1/realtime/ws");
  }

  private String header(HttpServletRequest request, String name) {
    String value = request.getHeader(name);
    return value == null || value.isBlank() ? "none" : value;
  }

  private void authenticate(HttpServletRequest request, Claims claims) {
    UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(claims.getSubject(), null, List.of());
    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }
}
