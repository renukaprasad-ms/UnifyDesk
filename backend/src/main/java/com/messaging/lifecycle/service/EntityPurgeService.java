package com.messaging.lifecycle.service;

import com.messaging.company.enums.CompanyStatus;
import com.messaging.company.repository.CompanyRepository;
import com.messaging.kafka.event.EntityDeletionRequestedEvent;
import com.messaging.lifecycle.entity.DeletionPurgeJob;
import com.messaging.lifecycle.enums.LifecycleEntityType;
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

  public void purge(DeletionPurgeJob job) {
    switch (job.getEntityType()) {
      case COMPANY -> purgeCompany(job);
      case USER -> purgeUser(job);
      case CHANNEL_CONNECTION, COMPANY_ROLE -> {
        // These entity types use immediate logical cleanup and are not 30-day roots.
      }
    }
  }

  private void purgeCompany(DeletionPurgeJob job) {
    Long companyId = job.getEntityId();
    var company = companyRepository.findById(companyId).orElse(null);
    if (company == null || company.getStatus() == CompanyStatus.PURGED) return;
    if (company.getStatus() != CompanyStatus.DELETION_PENDING
        && company.getStatus() != CompanyStatus.PURGING) {
      return;
    }

    markCompanyPurging(companyId);
    cleanupService.cleanup(
        new EntityDeletionRequestedEvent(
            LifecycleEntityType.COMPANY,
            companyId,
            companyId,
            job.getRequestedByUserId(),
            Instant.now()));
    purgeCompanyOperationalData(companyId);
    markCompanyPurged(companyId);
  }

  private void purgeUser(DeletionPurgeJob job) {
    Long userId = job.getEntityId();
    var user = userRepository.findById(userId).orElse(null);
    if (user == null || user.getStatus() == UserStatus.PURGED) return;
    if (user.getStatus() != UserStatus.DELETION_PENDING && user.getStatus() != UserStatus.PURGING) {
      return;
    }

    markUserPurging(userId);
    cleanupService.cleanup(
        new EntityDeletionRequestedEvent(
            LifecycleEntityType.USER, userId, null, job.getRequestedByUserId(), Instant.now()));
    purgeUserOperationalData(userId);
    markUserPurged(userId);
  }

  @Transactional
  protected void markCompanyPurging(Long companyId) {
    companyRepository
        .findById(companyId)
        .ifPresent(
            company -> {
              company.setStatus(CompanyStatus.PURGING);
              company.setPurgeStartedAt(Instant.now());
              companyRepository.saveAndFlush(company);
            });
  }

  @Transactional
  protected void purgeCompanyOperationalData(Long companyId) {
    jdbcTemplate.update(
        "delete from company_invitation_roles where invitation_id in "
            + "(select id from company_invitations where company_id = ?)",
        companyId);
    jdbcTemplate.update("delete from company_invitations where company_id = ?", companyId);

    jdbcTemplate.update("delete from gmail_sync_batches where company_id = ?", companyId);
    jdbcTemplate.update("delete from gmail_sync_runs where company_id = ?", companyId);

    jdbcTemplate.update(
        "delete from inbox_message_attachments where message_id in "
            + "(select m.id from inbox_messages m join inbox_conversations c on c.id=m.conversation_id "
            + "where c.company_id = ?)",
        companyId);
    jdbcTemplate.update(
        "delete from inbox_participants where conversation_id in "
            + "(select id from inbox_conversations where company_id = ?)",
        companyId);
    jdbcTemplate.update(
        "delete from inbox_messages where conversation_id in "
            + "(select id from inbox_conversations where company_id = ?)",
        companyId);
    jdbcTemplate.update("delete from inbox_conversations where company_id = ?", companyId);

    jdbcTemplate.update(
        "delete from channel_sync_states where connection_id in "
            + "(select id from channel_connections where company_id = ?)",
        companyId);
    jdbcTemplate.update(
        "delete from provider_credentials where connection_id in "
            + "(select id from channel_connections where company_id = ?)",
        companyId);
    jdbcTemplate.update(
        "delete from sender_identities where connection_id in "
            + "(select id from channel_connections where company_id = ?)",
        companyId);
    jdbcTemplate.update("delete from async_jobs where company_id = ?", companyId);
    jdbcTemplate.update("delete from channel_connections where company_id = ?", companyId);

    jdbcTemplate.update("delete from company_member_role_assignments where company_id = ?", companyId);
    jdbcTemplate.update(
        "delete from company_custom_role_permissions where role_id in "
            + "(select id from company_custom_roles where company_id = ?)",
        companyId);
    jdbcTemplate.update("delete from company_custom_roles where company_id = ?", companyId);

    jdbcTemplate.update("delete from company_member_access_profile_grants where company_id = ?", companyId);
    jdbcTemplate.update("delete from company_member_permission_overrides where company_id = ?", companyId);
    jdbcTemplate.update(
        "delete from company_access_profile_permissions where profile_id in "
            + "(select id from company_access_profiles where company_id = ?)",
        companyId);
    jdbcTemplate.update("delete from company_access_profiles where company_id = ?", companyId);
    jdbcTemplate.update("delete from company_role_permissions where company_id = ?", companyId);
    jdbcTemplate.update("delete from company_members where company_id = ?", companyId);

    jdbcTemplate.update("delete from admin_company_assignments where company_id = ?", companyId);
    jdbcTemplate.update("delete from company_rate_overrides where company_id = ?", companyId);
    jdbcTemplate.update("delete from company_storage_usage where company_id = ?", companyId);
    jdbcTemplate.update("delete from media where company_id = ?", companyId);
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
              user.setStatus(UserStatus.PURGING);
              user.setPurgeStartedAt(Instant.now());
              userRepository.saveAndFlush(user);
            });
  }

  @Transactional
  protected void purgeUserOperationalData(Long userId) {
    jdbcTemplate.update(
        "delete from gmail_sync_batches where job_id in (select id from async_jobs where user_id = ?)",
        userId);
    jdbcTemplate.update(
        "delete from gmail_sync_runs where job_id in (select id from async_jobs where user_id = ?)",
        userId);
    jdbcTemplate.update("delete from async_jobs where user_id = ?", userId);
    jdbcTemplate.update("delete from user_sessions where user_id = ?", userId);
    jdbcTemplate.update("delete from company_member_role_assignments where user_id = ?", userId);
    jdbcTemplate.update("delete from company_member_access_profile_grants where user_id = ?", userId);
    jdbcTemplate.update("delete from company_member_permission_overrides where user_id = ?", userId);
    jdbcTemplate.update("delete from company_members where user_id = ?", userId);
    jdbcTemplate.update("delete from admin_company_assignments where admin_user_id = ?", userId);
    jdbcTemplate.update(
        "delete from platform_admin_permissions where platform_admin_id in "
            + "(select id from platform_admins where user_id = ?)",
        userId);
    jdbcTemplate.update("delete from platform_admins where user_id = ?", userId);
    jdbcTemplate.update("update company_invitations set accepted_by_user_id = null where accepted_by_user_id = ?", userId);
    jdbcTemplate.update("delete from media where owner_user_id = ? and company_id is null", userId);
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
