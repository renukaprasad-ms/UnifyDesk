package com.messaging.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.common.response.ApiResponse;
import com.messaging.security.filter.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

  private final JwtAuthenticationFilter jwtAuthenticationFilter;
  private final ObjectMapper objectMapper;

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
    csrfTokenRepository.setCookiePath("/");

    CsrfTokenRequestAttributeHandler csrfTokenRequestHandler =
        new CsrfTokenRequestAttributeHandler();

    http.csrf(
            csrf ->
                csrf.csrfTokenRepository(csrfTokenRepository)
                    .csrfTokenRequestHandler(csrfTokenRequestHandler)
                    .ignoringRequestMatchers(
                        "/api/auth/register",
                        "/api/auth/login",
                        "/api/auth/2fa/verify",
                        "/api/auth/refresh",
                        "/api/v1/media/uploads",
                        "/api/v1/webhooks/meta/whatsapp",
                        "/api/v1/webhooks/meta/ads",
                        "/api/v1/webhooks/microsoft",
                        "/api/v1/webhooks/google/gmail"))
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/api/auth/register",
                        "/api/auth/csrf",
                        "/api/v1/media/uploads",
                        "/api/auth/login",
                        "/api/auth/2fa/verify",
                        "/api/auth/refresh",
                        "/api/auth/password-reset/request",
                        "/api/auth/password-reset/verify",
                        "/api/auth/password-reset/complete",
                        "/api/v1/webhooks/meta/whatsapp",
                        "/api/v1/webhooks/meta/ads",
                        "/api/v1/webhooks/microsoft",
                        "/api/v1/webhooks/google/gmail",
                        "/api/v1/oauth/gmail/callback",
                        "/api/v1/oauth/microsoft/callback",
                        "/api/v1/oauth/meta-ads/callback",
                        "/api/v1/oauth/google-ads/callback",
                        "/api/v1/email-assets/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            exception ->
                exception.authenticationEntryPoint(
                    (request, response, authException) -> {
                      response.setStatus(HttpStatus.UNAUTHORIZED.value());
                      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                      objectMapper.writeValue(
                          response.getOutputStream(),
                          ApiResponse.error(
                              HttpStatus.UNAUTHORIZED.value(), "Authentication required"));
                    }))
        .addFilterBefore(jwtAuthenticationFilter, CsrfFilter.class);

    return http.build();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
}
