package com.messaging.security.filter;

import com.messaging.auth.service.AuthCookieService;
import com.messaging.security.jwt.JwtService;
import com.messaging.user.enums.UserStatus;
import com.messaging.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final AuthCookieService authCookieService;
  private final JwtService jwtService;
  private final UserRepository userRepository;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (SecurityContextHolder.getContext().getAuthentication() != null) {
      filterChain.doFilter(request, response);
      return;
    }

    authCookieService
        .accessToken(request)
        .flatMap(jwtService::parseAccessToken)
        .ifPresent(claims -> authenticate(request, claims));

    filterChain.doFilter(request, response);
  }

  private void authenticate(HttpServletRequest request, Claims claims) {
    long userId;
    try {
      userId = Long.parseLong(claims.getSubject());
    } catch (NumberFormatException exception) {
      return;
    }
    boolean active =
        userRepository
            .findById(userId)
            .map(user -> user.getStatus() == UserStatus.ACTIVE)
            .orElse(false);
    if (!active) {
      return;
    }

    UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(claims.getSubject(), null, List.of());
    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }
}
