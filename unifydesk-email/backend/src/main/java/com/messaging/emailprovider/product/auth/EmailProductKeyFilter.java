package com.messaging.emailprovider.product.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class EmailProductKeyFilter extends OncePerRequestFilter {
  public static final String CONTEXT = "unifydesk.email.authenticated.project";
  private final EmailProjectApiKeyService authentication;

  public EmailProductKeyFilter(EmailProjectApiKeyService authentication) {
    this.authentication = authentication;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return request.getRequestURI().startsWith("/v1/unsubscribe/")
        || !request.getRequestURI().startsWith("/v1/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String path = request.getRequestURI();
    ApiKeyScope scope =
        path.startsWith("/v1/project")
            ? ApiKeyScope.DOMAINS
            : path.startsWith("/v1/webhook-endpoints")
                ? ApiKeyScope.WEBHOOKS
                : path.startsWith("/v1/domains")
                    ? ApiKeyScope.DOMAINS
                    : path.startsWith("/v1/validations")
                        ? ApiKeyScope.VALIDATE
                        : "POST".equals(request.getMethod()) ? ApiKeyScope.SEND : ApiKeyScope.READ;

    AuthenticatedProject project;
    try {
      project = authentication.authenticate(request.getHeader("Authorization"), scope);
    } catch (RuntimeException unavailable) {
      response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
      return;
    }
    if (project == null) {
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
      return;
    }
    if (scope == ApiKeyScope.SEND && !project.sendingEnabled()) {
      response.sendError(HttpServletResponse.SC_FORBIDDEN, "Project sending is not enabled");
      return;
    }
    request.setAttribute(CONTEXT, project);
    chain.doFilter(request, response);
  }

  public static AuthenticatedProject require(HttpServletRequest request) {
    Object value = request.getAttribute(CONTEXT);
    if (value instanceof AuthenticatedProject project) return project;
    throw new IllegalStateException("Missing authenticated email project");
  }
}
