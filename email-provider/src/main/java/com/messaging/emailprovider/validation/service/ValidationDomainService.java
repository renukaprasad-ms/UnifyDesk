package com.messaging.emailprovider.validation.service;

import com.messaging.emailprovider.validation.core.DisposableDomainPolicy;
import com.messaging.emailprovider.validation.core.DomainValidationSnapshot;
import com.messaging.emailprovider.validation.dns.MailRoute;
import com.messaging.emailprovider.validation.dns.MailRouteResolver;
import com.messaging.emailprovider.validation.entity.EmailValidationDomainCache;
import com.messaging.emailprovider.validation.repository.EmailValidationDomainCacheRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ValidationDomainService {

  private final EmailValidationDomainCacheRepository repository;
  private final MailRouteResolver resolver;
  private final DisposableDomainPolicy disposableDomainPolicy;
  private final Duration ttl;

  public ValidationDomainService(
      EmailValidationDomainCacheRepository repository,
      MailRouteResolver resolver,
      DisposableDomainPolicy disposableDomainPolicy,
      @Value("${app.validation.cache.domain-ttl-seconds:21600}") long ttlSeconds) {
    this.repository = repository;
    this.resolver = resolver;
    this.disposableDomainPolicy = disposableDomainPolicy;
    this.ttl = Duration.ofSeconds(Math.max(60, ttlSeconds));
  }

  @Transactional
  public DomainValidationSnapshot get(String domain) {
    Instant now = Instant.now();
    return repository
        .findByDomainAndExpiresAtAfter(domain, now)
        .map(this::snapshot)
        .orElseGet(() -> resolveAndStore(domain, now));
  }

  @Transactional
  public void updateCatchAll(String domain, boolean catchAll) {
    repository
        .findByDomain(domain)
        .ifPresent(
            row -> {
              row.setCatchAll(catchAll);
              repository.save(row);
            });
  }

  private DomainValidationSnapshot resolveAndStore(String domain, Instant now) {
    MailRoute route = resolver.resolve(domain);
    EmailValidationDomainCache row =
        repository.findByDomain(domain).orElseGet(EmailValidationDomainCache::new);
    row.setDomain(domain);
    row.setMailRoutable(route.routable());
    row.setNullMx(route.nullMx());
    row.setMxFound(route.mxFound());
    row.setMxHosts(String.join("\n", route.hosts()));
    row.setDisposable(disposableDomainPolicy.isDisposable(domain));
    row.setDnsLookupFailed(route.lookupFailed());
    row.setCheckedAt(now);
    row.setExpiresAt(now.plus(ttl));

    try {
      return snapshot(repository.save(row));
    } catch (DataIntegrityViolationException race) {
      return repository
          .findByDomain(domain)
          .map(this::snapshot)
          .orElseThrow(() -> race);
    }
  }

  private DomainValidationSnapshot snapshot(EmailValidationDomainCache row) {
    List<String> hosts =
        row.getMxHosts() == null || row.getMxHosts().isBlank()
            ? List.of()
            : Arrays.stream(row.getMxHosts().split("\n"))
                .filter(value -> !value.isBlank())
                .toList();
    return new DomainValidationSnapshot(
        row.getDomain(),
        row.isMailRoutable(),
        row.isNullMx(),
        row.isMxFound(),
        row.isDnsLookupFailed(),
        row.isDisposable(),
        row.getCatchAll(),
        hosts);
  }
}
