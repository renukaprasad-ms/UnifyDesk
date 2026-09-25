package com.messaging.emailprovider.validation.repository;

import com.messaging.emailprovider.validation.entity.EmailValidationDomainCache;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailValidationDomainCacheRepository
    extends JpaRepository<EmailValidationDomainCache, Long> {

  Optional<EmailValidationDomainCache> findByDomainAndExpiresAtAfter(String domain, Instant now);

  Optional<EmailValidationDomainCache> findByDomain(String domain);

  @Modifying
  @Query(
      value =
          """
          INSERT INTO email_validation_domain_cache (
              id, domain, mail_routable, null_mx, mx_found, mx_hosts, disposable,
              catch_all, dns_lookup_failed, checked_at, expires_at, created_at, updated_at
          ) VALUES (
              :id, :domain, :mailRoutable, :nullMx, :mxFound, :mxHosts, :disposable,
              NULL, :dnsLookupFailed, :checkedAt, :expiresAt, :now, :now
          )
          ON CONFLICT (domain) DO UPDATE SET
              mail_routable = EXCLUDED.mail_routable,
              null_mx = EXCLUDED.null_mx,
              mx_found = EXCLUDED.mx_found,
              mx_hosts = EXCLUDED.mx_hosts,
              disposable = EXCLUDED.disposable,
              dns_lookup_failed = EXCLUDED.dns_lookup_failed,
              checked_at = EXCLUDED.checked_at,
              expires_at = EXCLUDED.expires_at,
              updated_at = EXCLUDED.updated_at
          """,
      nativeQuery = true)
  void upsert(
      @Param("id") Long id,
      @Param("domain") String domain,
      @Param("mailRoutable") boolean mailRoutable,
      @Param("nullMx") boolean nullMx,
      @Param("mxFound") boolean mxFound,
      @Param("mxHosts") String mxHosts,
      @Param("disposable") boolean disposable,
      @Param("dnsLookupFailed") boolean dnsLookupFailed,
      @Param("checkedAt") Instant checkedAt,
      @Param("expiresAt") Instant expiresAt,
      @Param("now") Instant now);
}
