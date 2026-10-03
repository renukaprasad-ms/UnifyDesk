package com.messaging.lifecycle.service;

import com.messaging.company.enums.CompanyStatus;
import com.messaging.company.repository.CompanyRepository;
import com.messaging.contacts.processing.ContactCompanyPurgeService;
import com.messaging.lifecycle.config.DeletionLifecycleProperties;
import com.messaging.lifecycle.entity.DeletionPurgeJob;
import com.messaging.notification.platform.core.cleanup.NotificationCoreCleanupService;
import com.messaging.notification.platform.delivery.service.NotificationDeliveryCleanupService;
import com.messaging.user.enums.UserStatus;
import com.messaging.user.repository.UserRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EntityPurgeService {

  private final JdbcTemplate jdbcTemplate;
  private final CompanyRepository companyRepository;
  private final UserRepository userRepository;
  private final DeletionCleanupService cleanupService;
  private final DeletionLifecycleProperties properties;
  private final NotificationDeliveryCleanupService notificationDeliveryCleanupService;
  private final NotificationCoreCleanupService notificationCoreCleanupService;
  private final ContactCompanyPurgeService contactCompanyPurgeService;

  /**
   * Processes bounded purge work. Returns true only when the root entity reached its final PURGED
   * tombstone state. Returning false yields the job back to the scheduler for a later pass.
   */
  public boolean purge(DeletionPurgeJob job) {
    return switch (job.getEntityType()) {
      case COMPANY -> purgeCompany(job.getEntityId());
      case USER -> purgeUser(job.getEntityId());
      case CHANNEL_CONNECTION -> purgeChannelConnection(job.getEntityId());
      case COMPANY_ROLE -> true;
    };
  }

  private boolean purgeChannelConnection(Long connectionId) {
    Integer eligible =
        jdbcTemplate.queryForObject(
            """
            select count(*)
            from channel_connections
            where id = ?
              and status = 'DISCONNECTED'
              and purge_after is not null
              and purge_after <= now()
            """,
            Integer.class,
            connectionId);
    if (eligible == null || eligible == 0) {
      return true;
    }

    if (!drainRows(
        "delete from gmail_sync_batches where ctid in "
            + "(select ctid from gmail_sync_batches where connection_id = ? limit ?)",
        connectionId)) return false;

    if (!drainRows(
        "delete from gmail_sync_runs where ctid in "
            + "(select ctid from gmail_sync_runs where connection_id = ? limit ?)",
        connectionId)) return false;

    if (!drainRows(
        "delete from microsoft_backfill_runs where ctid in "
            + "(select ctid from microsoft_backfill_runs where connection_id = ? limit ?)",
        connectionId)) return false;

    jdbcTemplate.update(
        """
        delete from channel_connections
        where id = ?
          and status = 'DISCONNECTED'
          and purge_after is not null
          and purge_after <= now()
        """,
        connectionId);
    return true;
  }

  private boolean purgeCompany(Long companyId) {
    var company = companyRepository.findById(companyId).orElse(null);
    if (company == null || company.getStatus() == CompanyStatus.PURGED) return true;
    if (company.getStatus() != CompanyStatus.DELETION_PENDING
        && company.getStatus() != CompanyStatus.PURGING) {
      return true;
    }

    markCompanyPurging(companyId);

    int mediaBatchSize = Math.max(1, properties.getMediaBatchSize());
    if (cleanupService.cleanupCompanyMediaBatch(companyId, mediaBatchSize) >= mediaBatchSize) {
      return false;
    }

    int notificationBatchSize = Math.max(1, properties.getRowBatchSize());
    if (!notificationDeliveryCleanupService.purgeCompanyBatch(companyId, notificationBatchSize)) {
      return false;
    }
    if (!notificationCoreCleanupService.purgeCompanyBatch(companyId, notificationBatchSize)) {
      return false;
    }

    if (!drain(
        "delete from gmail_sync_batches where ctid in "
            + "(select ctid from gmail_sync_batches where company_id = ? limit ?)",
        companyId)) return false;

    if (!drain(
        "delete from gmail_sync_runs where ctid in "
            + "(select ctid from gmail_sync_runs where company_id = ? limit ?)",
        companyId)) return false;

    if (!drain(
        "delete from inbox_message_attachments where ctid in ("
            + "select a.ctid from inbox_message_attachments a "
            + "join inbox_messages m on m.id = a.message_id "
            + "join inbox_conversations c on c.id = m.conversation_id "
            + "where c.company_id = ? limit ?)",
        companyId)) return false;

    if (!drain(
        "delete from inbox_participants where ctid in ("
            + "select p.ctid from inbox_participants p "
            + "join inbox_conversations c on c.id = p.conversation_id "
            + "where c.company_id = ? limit ?)",
        companyId)) return false;

    if (!drain(
        "delete from inbox_messages where ctid in ("
            + "select m.ctid from inbox_messages m "
            + "join inbox_conversations c on c.id = m.conversation_id "
            + "where c.company_id = ? limit ?)",
        companyId)) return false;

    if (!drain(
        "delete from inbox_conversations where ctid in "
            + "(select ctid from inbox_conversations where company_id = ? limit ?)",
        companyId)) return false;

    if (!drain(
        "delete from channel_sync_states where ctid in ("
            + "select s.ctid from channel_sync_states s "
            + "join channel_connections c on c.id = s.connection_id "
            + "where c.company_id = ? limit ?)",
        companyId)) return false;

    if (!drain(
        "delete from provider_credentials where ctid in ("
            + "select p.ctid from provider_credentials p "
            + "join channel_connections c on c.id = p.connection_id "
            + "where c.company_id = ? limit ?)",
        companyId)) return false;

    if (!drain(
        "delete from sender_identities where ctid in ("
            + "select s.ctid from sender_identities s "
            + "join channel_connections c on c.id = s.connection_id "
            + "where c.company_id = ? limit ?)",
        companyId)) return false;

    if (!drain(
        "delete from async_jobs where ctid in "
            + "(select ctid from async_jobs where company_id = ? limit ?)",
        companyId)) return false;

    if (!contactCompanyPurgeService.purgeCompanyBatch(
        companyId, Math.max(1, properties.getRowBatchSize()))) {
      return false;
    }

    if (!drain(
        "delete from channel_connections where ctid in "
            + "(select ctid from channel_connections where company_id = ? limit ?)",
        companyId)) return false;

    if (!drain(
        "delete from company_invitation_roles where ctid in ("
            + "select r.ctid from company_invitation_roles r "
            + "join company_invitations i on i.id = r.invitation_id "
            + "where i.company_id = ? limit ?)",
        companyId)) return false;

    if (!drain(
        "delete from company_invitations where ctid in "
            + "(select ctid from company_invitations where company_id = ? limit ?)",
        companyId)) return false;

    if (!drain(
        "delete from company_member_role_assignments where ctid in "
            + "(select ctid from company_member_role_assignments where company_id = ? limit ?)",
        companyId)) return false;

    if (!drain(
        "delete from company_custom_role_permissions where ctid in ("
            + "select p.ctid from company_custom_role_permissions p "
            + "join company_custom_roles r on r.id = p.role_id "
            + "where r.company_id = ? limit ?)",
        companyId)) return false;

    if (!drain(
        "delete from company_custom_roles where ctid in "
            + "(select ctid from company_custom_roles where company_id = ? limit ?)",
        companyId)) return false;

    if (!drain(
        "delete from company_member_access_profile_grants where ctid in "
            + "(select ctid from company_member_access_profile_grants where company_id = ? limit ?)",
        companyId)) return false;

    if (!drain(
        "delete from company_member_permission_overrides where ctid in "
            + "(select ctid from company_member_permission_overrides where company_id = ? limit ?)",
        companyId)) return false;

    if (!drain(
        "delete from company_access_profile_permissions where ctid in ("
            + "select p.ctid from company_access_profile_permissions p "
            + "join company_access_profiles a on a.id = p.profile_id "
            + "where a.company_id = ? limit ?)",
        companyId)) return false;

    if (!drain(
        "delete from company_access_profiles where ctid in "
            + "(select ctid from company_access_profiles where company_id = ? limit ?)",
        companyId)) return false;

    if (!drain(
        "delete from company_role_permissions where ctid in "
            + "(select ctid from company_role_permissions where company_id = ? limit ?)",
        companyId)) return false;

    if (!drain(
        "delete from company_members where ctid in "
            + "(select ctid from company_members where company_id = ? limit ?)",
        companyId)) return false;

    if (!drain(
        "delete from admin_company_assignments where ctid in "
            + "(select ctid from admin_company_assignments where company_id = ? limit ?)",
        companyId)) return false;

    if (!drain(
        "delete from company_storage_usage where ctid in "
            + "(select ctid from company_storage_usage where company_id = ? limit ?)",
        companyId)) return false;

    if (!drain(
        "delete from media where ctid in "
            + "(select ctid from media where company_id = ? limit ?)",
        companyId)) return false;

    markCompanyPurged(companyId);
    return true;
  }

  private boolean purgeUser(Long userId) {
    var user = userRepository.findById(userId).orElse(null);
    if (user == null || user.getStatus() == UserStatus.PURGED) return true;
    if (user.getStatus() != UserStatus.DELETION_PENDING && user.getStatus() != UserStatus.PURGING) {
      return true;
    }

    markUserPurging(userId);

    int mediaBatchSize = Math.max(1, properties.getMediaBatchSize());
    if (cleanupService.cleanupPersonalMediaBatch(userId, mediaBatchSize) >= mediaBatchSize) {
      return false;
    }

    int notificationBatchSize = Math.max(1, properties.getRowBatchSize());
    if (!notificationDeliveryCleanupService.purgeUserBatch(userId, notificationBatchSize)) {
      return false;
    }
    if (!notificationCoreCleanupService.purgeUserBatch(userId, notificationBatchSize)) {
      return false;
    }

    if (!drainByUser(
        "delete from gmail_sync_batches where ctid in ("
            + "select b.ctid from gmail_sync_batches b join async_jobs j on j.id=b.job_id "
            + "where j.user_id = ? limit ?)",
        userId)) return false;

    if (!drainByUser(
        "delete from gmail_sync_runs where ctid in ("
            + "select r.ctid from gmail_sync_runs r join async_jobs j on j.id=r.job_id "
            + "where j.user_id = ? limit ?)",
        userId)) return false;

    if (!drainByUser(
        "delete from async_jobs where ctid in "
            + "(select ctid from async_jobs where user_id = ? limit ?)",
        userId)) return false;

    if (!drainByUser(
        "delete from user_sessions where ctid in "
            + "(select ctid from user_sessions where user_id = ? limit ?)",
        userId)) return false;

    if (!drainByUser(
        "delete from company_member_role_assignments where ctid in "
            + "(select ctid from company_member_role_assignments where user_id = ? limit ?)",
        userId)) return false;

    if (!drainByUser(
        "delete from company_member_access_profile_grants where ctid in "
            + "(select ctid from company_member_access_profile_grants where user_id = ? limit ?)",
        userId)) return false;

    if (!drainByUser(
        "delete from company_member_permission_overrides where ctid in "
            + "(select ctid from company_member_permission_overrides where user_id = ? limit ?)",
        userId)) return false;

    if (!drainByUser(
        "delete from company_members where ctid in "
            + "(select ctid from company_members where user_id = ? limit ?)",
        userId)) return false;

    if (!drainByUser(
        "delete from admin_company_assignments where ctid in "
            + "(select ctid from admin_company_assignments where admin_user_id = ? limit ?)",
        userId)) return false;

    if (!drainByUser(
        "delete from platform_admin_permissions where ctid in ("
            + "select p.ctid from platform_admin_permissions p "
            + "join platform_admins a on a.id=p.platform_admin_id "
            + "where a.user_id = ? limit ?)",
        userId)) return false;

    if (!drainByUser(
        "delete from platform_admins where ctid in "
            + "(select ctid from platform_admins where user_id = ? limit ?)",
        userId)) return false;

    jdbcTemplate.update(
        "update company_invitations set accepted_by_user_id = null where accepted_by_user_id = ?",
        userId);

    if (!drainByUser(
        "delete from media where ctid in "
            + "(select ctid from media where owner_user_id = ? and company_id is null limit ?)",
        userId)) return false;

    markUserPurged(userId);
    return true;
  }

  private boolean drain(String sql, Long companyId) {
    return drainRows(sql, companyId);
  }

  private boolean drainByUser(String sql, Long userId) {
    return drainRows(sql, userId);
  }

  private boolean drainRows(String sql, Long rootId) {
    int rowBatchSize = Math.max(1, properties.getRowBatchSize());
    int maxBatches = Math.max(1, properties.getMaxRowBatchesPerRun());

    for (int batch = 0; batch < maxBatches; batch++) {
      int deleted = jdbcTemplate.update(sql, rootId, rowBatchSize);
      if (deleted < rowBatchSize) {
        return true;
      }
    }
    return false;
  }

  @Transactional
  protected void markCompanyPurging(Long companyId) {
    companyRepository
        .findById(companyId)
        .ifPresent(
            company -> {
              if (company.getStatus() == CompanyStatus.DELETION_PENDING) {
                company.setStatus(CompanyStatus.PURGING);
                company.setPurgeStartedAt(Instant.now());
                companyRepository.saveAndFlush(company);
              }
            });
  }

  @Transactional
  protected void markCompanyPurged(Long companyId) {
    companyRepository
        .findById(companyId)
        .ifPresent(
            company -> {
              Instant now = Instant.now();
              company.setStatus(CompanyStatus.PURGED);
              company.setPurgedAt(now);
              company.setPurgeAfter(null);
              company.setName("Purged company");
              company.setSlug("purged-" + companyId);
              company.setBusinessEmail(null);
              company.setCountry(null);
              company.setTimezone(null);
              company.setLogoMediaId(null);
              companyRepository.saveAndFlush(company);
            });
  }

  @Transactional
  protected void markUserPurging(Long userId) {
    userRepository
        .findById(userId)
        .ifPresent(
            user -> {
              if (user.getStatus() == UserStatus.DELETION_PENDING) {
                user.setStatus(UserStatus.PURGING);
                user.setPurgeStartedAt(Instant.now());
                userRepository.saveAndFlush(user);
              }
            });
  }

  @Transactional
  protected void markUserPurged(Long userId) {
    userRepository
        .findById(userId)
        .ifPresent(
            user -> {
              Instant now = Instant.now();
              user.setStatus(UserStatus.PURGED);
              user.setPurgedAt(now);
              user.setPurgeAfter(null);
              user.setEmail("purged-" + userId + "@deleted.invalid");
              user.setUsername("purged_" + userId);
              user.setName("Purged user");
              user.setVerified(false);
              user.setTwoFactorEnabled(false);
              user.setProfilePicture(null);
              user.setPassword("PURGED");
              userRepository.saveAndFlush(user);
            });
  }
}
