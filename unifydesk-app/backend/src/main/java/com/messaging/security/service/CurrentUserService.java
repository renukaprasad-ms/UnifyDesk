package com.messaging.security.service;

import com.messaging.common.exception.UnauthorizedException;
import java.util.Optional;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

  public Long currentUserId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null
        || !authentication.isAuthenticated()
        || authentication instanceof AnonymousAuthenticationToken) {
      throw new UnauthorizedException("Authentication required");
    }
    return parseRequiredPrincipal(authentication.getPrincipal());
  }

  public Optional<Long> currentUserIdOrEmpty() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null
        || !authentication.isAuthenticated()
        || authentication instanceof AnonymousAuthenticationToken) {
      return Optional.empty();
    }
    try {
      return Optional.of(Long.parseLong(authentication.getPrincipal().toString()));
    } catch (NumberFormatException exception) {
      return Optional.empty();
    }
  }

  private Long parseRequiredPrincipal(Object principal) {
    if (principal == null) {
      throw new UnauthorizedException("Authentication required");
    }
    try {
      return Long.parseLong(principal.toString());
    } catch (NumberFormatException exception) {
      throw new UnauthorizedException("Invalid authentication principal");
    }
  }
}
