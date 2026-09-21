package com.messaging.admin.service;

import com.messaging.common.id.IdGenerator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SystemSuperAdminBootstrapService {

  private final JdbcTemplate jdbcTemplate;
  private final PasswordEncoder passwordEncoder;

  @Value("${BOOTSTRAP_SUPER_ADMIN_EMAIL:superadmin@renukaprasad.in}")
  private String email;

  @Value("${BOOTSTRAP_SUPER_ADMIN_USERNAME:superadmin}")
  private String username;

  @Value("${BOOTSTRAP_SUPER_ADMIN_NAME:Super Admin}")
  private String name;

  @Value("${BOOTSTRAP_SUPER_ADMIN_PASSWORD:}")
  private String initialPassword;

  @Transactional
  public Long ensureSuperAdmin() {
    String normalizedEmail = email.trim().toLowerCase();
    List<Long> users =
        jdbcTemplate.queryForList(
            "select id from users where lower(email) = lower(?) limit 1",
            Long.class,
            normalizedEmail);

    if (users.isEmpty()) {
      if (initialPassword == null || initialPassword.isBlank()) {
        throw new IllegalStateException(
            "BOOTSTRAP_SUPER_ADMIN_PASSWORD is required when the bootstrap Super Admin user does not exist");
      }

      Integer usernameCount =
          jdbcTemplate.queryForObject(
              "select count(*) from users where lower(username) = lower(?)",
              Integer.class,
              username.trim());
      if (usernameCount != null && usernameCount > 0) {
        throw new IllegalStateException(
            "Bootstrap Super Admin username is already used by another account");
      }

      jdbcTemplate.update(
          """
          insert into users
            (id, email, name, is_verified, username, password, two_factor_enabled, status)
          values (?, ?, ?, true, ?, ?, false, 'ACTIVE')
          on conflict (email) do nothing
          """,
          IdGenerator.nextId(),
          normalizedEmail,
          name.trim(),
          username.trim(),
          passwordEncoder.encode(initialPassword));

      users =
          jdbcTemplate.queryForList(
              "select id from users where lower(email) = lower(?) limit 1",
              Long.class,
              normalizedEmail);
    }

    if (users.isEmpty()) {
      throw new IllegalStateException("Unable to create bootstrap Super Admin user");
    }

    Long userId = users.getFirst();
    jdbcTemplate.update(
        """
        update users
        set is_verified = true,
            status = 'ACTIVE',
            deleted_at = null
        where id = ?
        """,
        userId);

    jdbcTemplate.update(
        """
        insert into platform_admins
          (id, user_id, role, status, created_at)
        values (?, ?, 'SUPER_ADMIN', 'ACTIVE', now())
        on conflict (user_id) do update set
          role = 'SUPER_ADMIN',
          status = 'ACTIVE'
        """,
        IdGenerator.nextId(),
        userId);

    return userId;
  }
}
