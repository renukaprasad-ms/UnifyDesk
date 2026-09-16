package com.messaging.admin.service;
import com.messaging.admin.dto.*;
import com.messaging.admin.enums.PlatformPermission;
import com.messaging.billing.entity.*;
import com.messaging.billing.enums.PlanStatus;
import com.messaging.billing.repository.*;
import com.messaging.common.exception.NotFoundException;
import com.messaging.security.service.CurrentUserService;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class PlatformPlanManagementService {
 private final PlanRepository plans; private final PlanEntitlementRepository entitlements; private final CurrentUserService currentUser; private final PlatformAdminAuthorizationService auth;
 @Transactional(readOnly=true) public Page<PlatformPlanResponse> list(String search,Pageable pageable){ auth.requirePermission(currentUser.currentUserId(),PlatformPermission.PLAN_VIEW); Page<Plan> p=search==null||search.isBlank()?plans.findAll(pageable):plans.findByNameContainingIgnoreCaseOrCodeContainingIgnoreCase(search.trim(),search.trim(),pageable); return p.map(this::response); }
 @Transactional(readOnly=true) public PlatformPlanResponse get(Long id){ auth.requirePermission(currentUser.currentUserId(),PlatformPermission.PLAN_VIEW); return response(find(id)); }
 @Transactional public PlatformPlanResponse create(PlatformPlanRequest r){ auth.requirePermission(currentUser.currentUserId(),PlatformPermission.PLAN_CREATE); String code=r.code().trim().toUpperCase(Locale.ROOT); if(plans.existsByCodeAndVersion(code,r.version())) throw new IllegalArgumentException("Plan code/version already exists"); Plan p=new Plan(); apply(p,r,code); p.setStatus(PlanStatus.ACTIVE); p=plans.save(p); replaceEntitlements(p,r.entitlements()); return response(p); }
 @Transactional public PlatformPlanResponse update(Long id,PlatformPlanRequest r){ auth.requirePermission(currentUser.currentUserId(),PlatformPermission.PLAN_UPDATE); Plan p=find(id); String code=r.code().trim().toUpperCase(Locale.ROOT); if((!p.getCode().equals(code)||p.getVersion()!=r.version())&&plans.existsByCodeAndVersion(code,r.version())) throw new IllegalArgumentException("Plan code/version already exists"); apply(p,r,code); replaceEntitlements(p,r.entitlements()); return response(p); }
 @Transactional public void archive(Long id){ auth.requirePermission(currentUser.currentUserId(),PlatformPermission.PLAN_DISABLE); Plan p=find(id); p.setStatus(PlanStatus.ARCHIVED); }
 private Plan find(Long id){ return plans.findById(id).orElseThrow(()->new NotFoundException("Plan not found")); }
 private void apply(Plan p,PlatformPlanRequest r,String code){ p.setCode(code);p.setName(r.name().trim());p.setBillingCycle(r.billingCycle());p.setBasePrice(r.basePrice());p.setCurrency(r.currency().toUpperCase(Locale.ROOT));p.setVersion(r.version()); }
 private void replaceEntitlements(Plan p,Map<String,String> values){ for(PlanEntitlement e:entitlements.findByPlanId(p.getId())) entitlements.delete(e); if(values==null)return; values.forEach((k,v)->{ if(k==null||k.isBlank()||v==null)return; PlanEntitlement e=new PlanEntitlement();e.setPlan(p);e.setKey(k.trim().toUpperCase(Locale.ROOT));e.setValue(v);entitlements.save(e);}); }
 private PlatformPlanResponse response(Plan p){ Map<String,String> map=new LinkedHashMap<>(); for(PlanEntitlement e:entitlements.findByPlanId(p.getId()))map.put(e.getKey(),e.getValue()); return new PlatformPlanResponse(p.getId().toString(),p.getCode(),p.getName(),p.getStatus(),p.getBillingCycle(),p.getBasePrice(),p.getCurrency(),p.getVersion(),map); }
}
