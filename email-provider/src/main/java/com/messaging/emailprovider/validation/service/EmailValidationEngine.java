package com.messaging.emailprovider.validation.service;

import com.messaging.emailprovider.validation.core.DomainValidationSnapshot;
import com.messaging.emailprovider.validation.core.NormalizedEmail;
import com.messaging.emailprovider.validation.core.RoleAddressPolicy;
import com.messaging.emailprovider.validation.core.ValidationDecision;
import com.messaging.emailprovider.validation.enums.SmtpProbeStatus;
import com.messaging.emailprovider.validation.enums.ValidationReason;
import com.messaging.emailprovider.validation.enums.ValidationStatus;
import com.messaging.emailprovider.validation.ratelimit.DomainProbeLimiter;
import com.messaging.emailprovider.validation.smtp.SmtpProbeClient;
import com.messaging.emailprovider.validation.smtp.SmtpProbeResult;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EmailValidationEngine {

  private final RoleAddressPolicy roleAddressPolicy;
  private final SmtpProbeClient smtpProbeClient;
  private final DomainProbeLimiter limiter;
  private final ValidationDomainService domainService;
  private final boolean smtpEnabled;
  private final boolean catchAllEnabled;

  public EmailValidationEngine(
      RoleAddressPolicy roleAddressPolicy,
      SmtpProbeClient smtpProbeClient,
      DomainProbeLimiter limiter,
      ValidationDomainService domainService,
      @Value("${app.validation.smtp.enabled:false}") boolean smtpEnabled,
      @Value("${app.validation.smtp.catch-all-enabled:true}") boolean catchAllEnabled) {
    this.roleAddressPolicy = roleAddressPolicy;
    this.smtpProbeClient = smtpProbeClient;
    this.limiter = limiter;
    this.domainService = domainService;
    this.smtpEnabled = smtpEnabled;
    this.catchAllEnabled = catchAllEnabled;
  }

  public ValidationDecision validate(
      NormalizedEmail email, DomainValidationSnapshot domain) {
    boolean role = roleAddressPolicy.isRoleAddress(email.localPart());

    if (domain.dnsLookupFailed()) {
      return decision(
          ValidationStatus.UNKNOWN,
          ValidationReason.DNS_TEMPORARY_FAILURE,
          25,
          domain,
          role,
          null,
          null,
          "DNS lookup failed");
    }
    if (domain.nullMx()) {
      return decision(
          ValidationStatus.INVALID,
          ValidationReason.NULL_MX,
          0,
          domain,
          role,
          null,
          null,
          "Domain explicitly does not accept email");
    }
    if (!domain.routable()) {
      return decision(
          ValidationStatus.INVALID,
          ValidationReason.NO_MAIL_ROUTE,
          0,
          domain,
          role,
          null,
          null,
          "No MX or address fallback route");
    }
    if (!smtpEnabled) {
      ValidationStatus status =
          domain.disposable() || role ? ValidationStatus.RISKY : ValidationStatus.UNKNOWN;
      ValidationReason reason =
          domain.disposable()
              ? ValidationReason.DISPOSABLE_DOMAIN
              : role ? ValidationReason.ROLE_ADDRESS : ValidationReason.SMTP_SKIPPED;
      return decision(
          status,
          reason,
          domain.disposable() ? 30 : role ? 65 : 70,
          domain,
          role,
          SmtpProbeStatus.SKIPPED,
          null,
          "SMTP probing disabled");
    }

    Boolean catchAll = domain.catchAll();
    if (catchAll == null && catchAllEnabled) {
      catchAll = detectCatchAll(email, domain);
      if (catchAll != null) {
        domainService.updateCatchAll(domain.domain(), catchAll);
      }
    }

    if (Boolean.TRUE.equals(catchAll)) {
      return decision(
          ValidationStatus.RISKY,
          ValidationReason.CATCH_ALL_DOMAIN,
          domain.disposable() ? 25 : 55,
          domain,
          role,
          SmtpProbeStatus.ACCEPTED,
          null,
          "Domain accepts a randomized mailbox");
    }

    try (DomainProbeLimiter.Lease lease = limiter.tryAcquire(domain.domain())) {
      if (!lease.acquired()) {
        return decision(
            ValidationStatus.UNKNOWN,
            ValidationReason.SMTP_UNAVAILABLE,
            50,
            domain,
            role,
            SmtpProbeStatus.UNAVAILABLE,
            null,
            "Domain probe rate limit reached");
      }
      SmtpProbeResult probe = smtpProbeClient.probe(domain.hosts(), email.value());
      return fromProbe(domain, role, probe);
    }
  }

  private Boolean detectCatchAll(
      NormalizedEmail email, DomainValidationSnapshot domain) {
    String randomRecipient =
        "__unifydesk_probe_" + UUID.randomUUID().toString().replace("-", "") + "@" + email.domain();
    try (DomainProbeLimiter.Lease lease = limiter.tryAcquire(domain.domain())) {
      if (!lease.acquired()) {
        return null;
      }
      SmtpProbeResult probe = smtpProbeClient.probe(domain.hosts(), randomRecipient);
      if (probe.status() == SmtpProbeStatus.ACCEPTED) {
        return true;
      }
      if (probe.status() == SmtpProbeStatus.REJECTED) {
        return false;
      }
      return null;
    }
  }

  private ValidationDecision fromProbe(
      DomainValidationSnapshot domain, boolean role, SmtpProbeResult probe) {
    return switch (probe.status()) {
      case ACCEPTED -> {
        if (domain.disposable()) {
          yield decision(
              ValidationStatus.RISKY,
              ValidationReason.DISPOSABLE_DOMAIN,
              35,
              domain,
              role,
              probe.status(),
              probe.code(),
              probe.diagnostic());
        }
        if (role) {
          yield decision(
              ValidationStatus.RISKY,
              ValidationReason.ROLE_ADDRESS,
              75,
              domain,
              true,
              probe.status(),
              probe.code(),
              probe.diagnostic());
        }
        yield decision(
            ValidationStatus.VALID,
            ValidationReason.MAILBOX_ACCEPTED,
            95,
            domain,
            false,
            probe.status(),
            probe.code(),
            probe.diagnostic());
      }
      case REJECTED ->
          decision(
              ValidationStatus.INVALID,
              ValidationReason.MAILBOX_REJECTED,
              0,
              domain,
              role,
              probe.status(),
              probe.code(),
              probe.diagnostic());
      case TEMPORARY ->
          decision(
              ValidationStatus.UNKNOWN,
              ValidationReason.MAILBOX_TEMPORARY,
              45,
              domain,
              role,
              probe.status(),
              probe.code(),
              probe.diagnostic());
      case UNAVAILABLE, SKIPPED ->
          decision(
              ValidationStatus.UNKNOWN,
              ValidationReason.SMTP_UNAVAILABLE,
              50,
              domain,
              role,
              probe.status(),
              probe.code(),
              probe.diagnostic());
    };
  }

  private ValidationDecision decision(
      ValidationStatus status,
      ValidationReason reason,
      int score,
      DomainValidationSnapshot domain,
      boolean role,
      SmtpProbeStatus smtpStatus,
      Integer smtpCode,
      String diagnostic) {
    return new ValidationDecision(
        status,
        reason,
        score,
        domain.routable(),
        domain.mxFound(),
        domain.disposable(),
        role,
        domain.catchAll(),
        smtpStatus,
        smtpCode,
        diagnostic);
  }
}
