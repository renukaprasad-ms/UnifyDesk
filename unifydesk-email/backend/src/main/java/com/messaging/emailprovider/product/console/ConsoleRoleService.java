package com.messaging.emailprovider.product.console;

import com.messaging.emailprovider.common.exception.*;
import com.messaging.emailprovider.common.id.IdGenerator;
import com.messaging.emailprovider.product.console.ConsoleAuthService.Principal;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsoleRoleService {
  private final JdbcTemplate jdbc;
  private final ConsoleSecurityService security;

  public record Role(String id, String name, Set<ConsolePermission> permissions) {}

  public record User(String id, String email, String role, String customRoleId, String status) {}

  public ConsoleRoleService(JdbcTemplate jdbc, ConsoleSecurityService security) {
    this.jdbc = jdbc;
    this.security = security;
  }

  public Set<ConsolePermission> permissions(long organization, long user, ConsoleRole base) {
    var rows =
        jdbc.query(
            """
        SELECT r.permissions FROM email_console_users u JOIN email_console_roles r
          ON r.id=u.custom_role_id AND r.organization_id=u.organization_id
        WHERE u.id=? AND u.organization_id=?
        """,
            (rs, row) -> rs.getString(1),
            user,
            organization);
    return rows.isEmpty() ? ConsolePermission.defaults(base) : parse(rows.getFirst());
  }

  @Transactional(readOnly = true)
  public List<Role> list(Principal actor) {
    requireOwner(actor);
    return jdbc.query(
        "SELECT id,name,permissions FROM email_console_roles WHERE organization_id=? ORDER BY name LIMIT 50",
        (rs, row) -> new Role(rs.getString(1), rs.getString(2), parse(rs.getString(3))),
        actor.organizationId());
  }

  @Transactional(readOnly = true)
  public List<User> users(Principal actor) {
    requireOwner(actor);
    return jdbc.query(
        "SELECT id,email,role,custom_role_id,status FROM email_console_users WHERE organization_id=? ORDER BY id LIMIT 100",
        (rs, row) ->
            new User(
                rs.getString(1),
                rs.getString(2),
                rs.getString(3),
                rs.getString(4),
                rs.getString(5)),
        actor.organizationId());
  }

  @Transactional
  public Role create(
      Principal actor,
      String name,
      Set<ConsolePermission> permissions,
      String password,
      String code) {
    ownerProof(actor, password, code);
    if (name == null || name.isBlank() || name.length() > 80 || permissions == null)
      throw new BadRequestException("Invalid role");
    jdbc.queryForObject(
        "SELECT id FROM email_product_organizations WHERE id=? FOR UPDATE",
        Long.class,
        actor.organizationId());
    Long count =
        jdbc.queryForObject(
            "SELECT count(*) FROM email_console_roles WHERE organization_id=?",
            Long.class,
            actor.organizationId());
    if (count != null && count >= 50)
      throw new ConflictException("Organization role limit reached");
    long id = IdGenerator.nextId();
    jdbc.update(
        "INSERT INTO email_console_roles(id,organization_id,name,permissions) VALUES(?,?,?,?)",
        id,
        actor.organizationId(),
        name.trim(),
        encode(permissions));
    audit(actor, null, id, "ROLE_CREATED");
    return new Role(Long.toString(id), name.trim(), Set.copyOf(permissions));
  }

  @Transactional
  public void update(
      Principal actor,
      long role,
      Set<ConsolePermission> permissions,
      String password,
      String code) {
    ownerProof(actor, password, code);
    if (permissions == null) throw new BadRequestException("Permissions required");
    int updated =
        jdbc.update(
            "UPDATE email_console_roles SET permissions=? WHERE id=? AND organization_id=?",
            encode(permissions),
            role,
            actor.organizationId());
    if (updated != 1) throw new NotFoundException("Role not found");
    jdbc.update(
        "UPDATE email_console_sessions s SET revoked_at=now() FROM email_console_users u WHERE s.user_id=u.id AND u.organization_id=? AND u.custom_role_id=? AND s.revoked_at IS NULL",
        actor.organizationId(),
        role);
    audit(actor, null, role, "ROLE_UPDATED");
  }

  @Transactional
  public void assign(Principal actor, long user, Long role, String password, String code) {
    ownerProof(actor, password, code);
    if (user == actor.userId())
      throw new BadRequestException("An owner cannot change their own role");
    var targets =
        jdbc.query(
            "SELECT role,custom_role_id FROM email_console_users WHERE id=? AND organization_id=? AND status='ACTIVE' FOR UPDATE",
            (rs, row) -> new String[] {rs.getString(1), rs.getString(2)},
            user,
            actor.organizationId());
    if (targets.isEmpty()) throw new NotFoundException("Active organization user not found");
    if ("OWNER".equals(targets.getFirst()[0]))
      throw new BadRequestException("Owner transfer requires a separate reviewed workflow");
    if (role != null
        && !Boolean.TRUE.equals(
            jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM email_console_roles WHERE id=? AND organization_id=?)",
                Boolean.class,
                role,
                actor.organizationId()))) throw new NotFoundException("Role not found");
    jdbc.update(
        "UPDATE email_console_users SET role='VIEWER',custom_role_id=?,updated_at=now() WHERE id=?",
        role,
        user);
    jdbc.update(
        "UPDATE email_console_sessions SET revoked_at=now() WHERE user_id=? AND revoked_at IS NULL",
        user);
    Long audited =
        role != null
            ? role
            : (targets.getFirst()[1] == null ? null : Long.valueOf(targets.getFirst()[1]));
    if (audited != null)
      audit(actor, user, audited, role == null ? "ROLE_REMOVED" : "ROLE_ASSIGNED");
  }

  private void ownerProof(Principal actor, String password, String code) {
    requireOwner(actor);
    security.reauthenticate(actor, password, code);
  }

  private void requireOwner(Principal actor) {
    if (actor.role() != ConsoleRole.OWNER)
      throw new ForbiddenException("Owner permission required");
    Boolean current =
        jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM email_console_users WHERE id=? AND organization_id=? AND role='OWNER' AND status='ACTIVE')",
            Boolean.class,
            actor.userId(),
            actor.organizationId());
    if (!Boolean.TRUE.equals(current)) throw new ForbiddenException("Owner permission required");
  }

  private void audit(Principal actor, Long target, long role, String action) {
    jdbc.update(
        "INSERT INTO email_console_role_audit(id,organization_id,actor_user_id,target_user_id,role_id,action) VALUES(?,?,?,?,?,?)",
        IdGenerator.nextId(),
        actor.organizationId(),
        actor.userId(),
        target,
        role,
        action);
  }

  private static String encode(Set<ConsolePermission> permissions) {
    return permissions.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
  }

  private static Set<ConsolePermission> parse(String value) {
    if (value == null || value.isBlank()) return Set.of();
    return Arrays.stream(value.split(","))
        .map(ConsolePermission::valueOf)
        .collect(Collectors.toUnmodifiableSet());
  }
}
