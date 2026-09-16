package com.messaging.admin.service;
import com.messaging.admin.dto.PlatformUserResponse;
import com.messaging.admin.enums.PlatformPermission;
import com.messaging.security.service.CurrentUserService;
import com.messaging.user.entity.User;
import com.messaging.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service @RequiredArgsConstructor
public class PlatformUserManagementService {
 private final UserRepository users; private final CurrentUserService currentUser; private final PlatformAdminAuthorizationService auth;
 @Transactional(readOnly=true) public Page<PlatformUserResponse> list(String search,Pageable pageable){
  auth.requirePermission(currentUser.currentUserId(),PlatformPermission.USER_VIEW);
  Page<User> page=search==null||search.isBlank()?users.findAll(pageable):users.findByNameContainingIgnoreCaseOrEmailContainingIgnoreCaseOrUsernameContainingIgnoreCase(search.trim(),search.trim(),search.trim(),pageable);
  return page.map(u->new PlatformUserResponse(u.getId().toString(),u.getName(),u.getEmail(),u.getUsername(),u.isVerified(),u.isTwoFactorEnabled()));
 }
}
