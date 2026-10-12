package com.messaging.emailprovider.product.console;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ConsoleAuthFilter extends OncePerRequestFilter {
  public static final String CONTEXT = "unifydesk.email.console.principal";
  private final ConsoleAuthService auth;

  public ConsoleAuthFilter(ConsoleAuthService auth) {
    this.auth = auth;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest req) {
    if (!req.getRequestURI().startsWith("/console/v1/")) return true;
    return "POST".equals(req.getMethod())
        && (req.getRequestURI().equals("/console/v1/sessions")
            || req.getRequestURI().equals("/console/v1/activate"));
  }

  @Override
  protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp,
      FilterChain chain) throws IOException, ServletException {
    ConsoleAuthService.Principal principal;
    try {
      principal = auth.authenticate(req.getHeader("Authorization"));
    } catch (RuntimeException unavailable) {
      resp.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
      return;
    }
    if (principal == null) {
      resp.sendError(HttpServletResponse.SC_UNAUTHORIZED);
      return;
    }
    req.setAttribute(CONTEXT, principal);
    chain.doFilter(req, resp);
  }

  public static ConsoleAuthService.Principal require(HttpServletRequest request) {
    Object principal = request.getAttribute(CONTEXT);
    if (principal instanceof ConsoleAuthService.Principal p) return p;
    throw new IllegalStateException("Email console session missing");
  }
}
