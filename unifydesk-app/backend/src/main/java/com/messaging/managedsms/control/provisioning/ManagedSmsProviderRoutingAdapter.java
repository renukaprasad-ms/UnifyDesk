package com.messaging.managedsms.control.provisioning;

import com.messaging.admin.service.PlatformManagedProviderService;
import com.messaging.managedsms.control.msg91.Msg91ManagedSmsProviderAdapter;
import com.messaging.managedsms.metareach.MetaReachManagedSmsProviderAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** Selects the appropriate provisioning/review policy without altering MSG91 contracts. */
@Component @Primary @RequiredArgsConstructor
public class ManagedSmsProviderRoutingAdapter implements ManagedSmsProviderAdapter {
  private final PlatformManagedProviderService routes;
  private final Msg91ManagedSmsProviderAdapter msg91;
  private final MetaReachManagedSmsProviderAdapter metareach;

  private ManagedSmsProviderAdapter current(Long companyId) {
    return "METAREACH_SMS".equals(routes.smsProviderForCompany(companyId)) ? metareach : msg91;
  }
  @Override public ProviderResult execute(ProviderRequest request) {
    return current(request.companyId()).execute(request);
  }
  @Override public BindingReadiness bindingReadiness(BindingContext context) {
    return current(context.binding().getCompanyId()).bindingReadiness(context);
  }
}
