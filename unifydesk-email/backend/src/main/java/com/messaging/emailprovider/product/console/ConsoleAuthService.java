package com.messaging.emailprovider.product.console;

import com.messaging.emailprovider.common.exception.BadRequestException;
import com.messaging.emailprovider.common.exception.ConflictException;
import com.messaging.emailprovider.common.exception.NotFoundException;
import com.messaging.emailprovider.common.exception.TooManyRequestsException;
import com.messaging.emailprovider.product.auth.ApiKeyCrypto;
import com.messaging.emailprovider.product.model.EmailOrganization;
import com.messaging.emailprovider.product.repo.EmailOrganizationRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsoleAuthService {
  private final ConsoleUserRepository users;
  private final ConsoleInvitationRepository invitations;
  private final ConsoleSessionRepository sessions;
  private final EmailOrganizationRepository organizations;
  private final PasswordHasher passwords;
  private final StringRedisTemplate redis;
  private final ConsoleSecurityService security;
  private final ConsoleRoleService roles;

  public ConsoleAuthService(
      ConsoleUserRepository users,
      ConsoleInvitationRepository invitations,
      ConsoleSessionRepository sessions,
      EmailOrganizationRepository organizations,
      PasswordHasher passwords,
      StringRedisTemplate redis,
      ConsoleSecurityService security,
      ConsoleRoleService roles) {
    this.users = users;
    this.invitations = invitations;
    this.sessions = sessions;
    this.organizations = organizations;
    this.passwords = passwords;
    this.redis = redis;
    this.security = security;
    this.roles = roles;
  }

  public record Invite(String userId, String invitationToken, Instant expiresAt) {}

  public record Login(
      String sessionToken,
      Instant expiresAt,
      String userId,
      String organizationId,
      String role,
      String email,
      java.util.Set<ConsolePermission> permissions) {}

  public record Principal(
      Long userId,
      Long organizationId,
      ConsoleRole role,
      String email,
      Long sessionId,
      java.util.Set<ConsolePermission> permissions) {
    public Principal(
        Long userId, Long organizationId, ConsoleRole role, String email, Long sessionId) {
      this(userId, organizationId, role, email, sessionId, ConsolePermission.defaults(role));
    }

    public boolean permits(ConsolePermission permission) {
      return permissions != null && permissions.contains(permission);
    }
  }

  @Transactional
  public Invite invite(Long organizationId, String email, String displayName, ConsoleRole role) {
    if (!organizations.existsById(organizationId)) {
      throw new NotFoundException("Email organization not found");
    }
    String normalized = normalize(email);
    if (normalized.length() > 320
        || displayName == null
        || displayName.isBlank()
        || displayName.length() > 120
        || role == null) {
      throw new BadRequestException("Invalid invitation");
    }
    ConsoleUser user = users.findByOrganizationIdAndEmail(organizationId, normalized).orElse(null);
    if (user != null && "ACTIVE".equals(user.getStatus())) {
      throw new ConflictException("Account already activated");
    }
    if (user == null) {
      user = new ConsoleUser();
      user.setOrganizationId(organizationId);
      user.setEmail(normalized);
      user.setDisplayName(displayName.trim());
    }
    user.setRole(role);
    user.setStatus("PENDING");
    user = users.save(user);
    String token = "ude_inv_" + ApiKeyCrypto.random(32);
    ConsoleInvitation invite = new ConsoleInvitation();
    invite.setUserId(user.getId());
    invite.setTokenHash(ApiKeyCrypto.hash(token));
    invite.setExpiresAt(Instant.now().plus(Duration.ofHours(24)));
    invite = invitations.save(invite);
    return new Invite(user.getId().toString(), token, invite.getExpiresAt());
  }

  @Transactional
  public void activate(String invitationToken, String password) {
    if (invitationToken == null || !invitationToken.startsWith("ude_inv_")) {
      throw new BadRequestException("Invalid invitation");
    }
    ConsoleInvitation invitation =
        invitations.findByTokenHash(ApiKeyCrypto.hash(invitationToken)).orElse(null);
    if (invitation == null
        || invitation.getAcceptedAt() != null
        || !invitation.getExpiresAt().isAfter(Instant.now())) {
      throw new BadRequestException("Invitation expired or already used");
    }
    ConsoleUser user =
        users
            .findLockedById(invitation.getUserId())
            .orElseThrow(() -> new NotFoundException("Account not found"));
    if (!"PENDING".equals(user.getStatus())) {
      throw new BadRequestException("Account already activated");
    }
    user.setPasswordHash(passwords.hash(password));
    user.setStatus("ACTIVE");
    users.save(user);
    invitation.setAcceptedAt(Instant.now());
    invitations.save(invitation);
  }

  @Transactional
  public Login login(Long organizationId, String email, String password, String address) {
    return login(organizationId, email, password, address, null);
  }

  @Transactional
  public Login login(
      Long organizationId, String email, String password, String address, String mfaCode) {
    String normalized = normalize(email);
    limitLogin(organizationId, normalized, address);
    ConsoleUser user =
        users.findLockedByOrganizationIdAndEmail(organizationId, normalized).orElse(null);
    if (user == null
        || !"ACTIVE".equals(user.getStatus())
        || !passwords.verify(password, user.getPasswordHash())) {
      throw new BadRequestException("Invalid email or password");
    }
    EmailOrganization org = organizations.findById(organizationId).orElse(null);
    if (org == null || !"ACTIVE".equals(org.getStatus())) {
      throw new BadRequestException("Account unavailable");
    }
    security.verifyLogin(user.getId(), organizationId, mfaCode);
    String token = "ude_session_" + ApiKeyCrypto.random(32);
    ConsoleSession session = new ConsoleSession();
    session.setUserId(user.getId());
    session.setTokenHash(ApiKeyCrypto.hash(token));
    session.setExpiresAt(Instant.now().plus(Duration.ofHours(8)));
    sessions.save(session);
    return new Login(
        token,
        session.getExpiresAt(),
        user.getId().toString(),
        organizationId.toString(),
        user.getRole().name(),
        user.getEmail(),
        roles.permissions(organizationId, user.getId(), user.getRole()));
  }

  @Transactional(readOnly = true)
  public Principal authenticate(String bearer) {
    if (bearer == null || !bearer.startsWith("Bearer ude_session_") || bearer.length() > 160)
      return null;
    String hash = ApiKeyCrypto.hash(bearer.substring(7));
    ConsoleSession session = sessions.findByTokenHash(hash).orElse(null);
    if (session == null
        || session.getRevokedAt() != null
        || !session.getExpiresAt().isAfter(Instant.now())) return null;
    ConsoleUser user = users.findById(session.getUserId()).orElse(null);
    if (user == null || !"ACTIVE".equals(user.getStatus())) return null;
    EmailOrganization org = organizations.findById(user.getOrganizationId()).orElse(null);
    if (org == null || !"ACTIVE".equals(org.getStatus())) return null;
    return new Principal(
        user.getId(),
        org.getId(),
        user.getRole(),
        user.getEmail(),
        session.getId(),
        roles.permissions(org.getId(), user.getId(), user.getRole()));
  }

  @Transactional
  public void logout(Principal user) {
    sessions
        .findById(user.sessionId())
        .ifPresent(
            session -> {
              session.setRevokedAt(Instant.now());
              sessions.save(session);
            });
  }

  private void limitLogin(Long orgId, String email, String address) {
    String key = "ud:email:console:login:" + orgId + ":" + ApiKeyCrypto.hash(email + "|" + address);
    try {
      Long count = redis.opsForValue().increment(key);
      if (count == null) throw new IllegalStateException("Redis rejected increment");
      if (count == 1) redis.expire(key, Duration.ofMinutes(5));
      if (count > 10) throw new TooManyRequestsException("Too many login attempts");
    } catch (TooManyRequestsException e) {
      throw e;
    } catch (RuntimeException unavailable) {
      throw new TooManyRequestsException("Authentication temporarily unavailable");
    }
  }

  private String normalize(String email) {
    if (email == null || email.isBlank()) throw new BadRequestException("Email is required");
    return email.trim().toLowerCase(Locale.ROOT);
  }
}
