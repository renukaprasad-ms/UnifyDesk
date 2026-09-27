package com.messaging.managedsms.control.msg91;

import static org.assertj.core.api.Assertions.assertThat;

import com.messaging.broadcast.enums.BroadcastSenderProvider;
import com.messaging.managedsms.control.config.ManagedSmsProperties;
import com.messaging.managedsms.control.entity.*;
import com.messaging.managedsms.control.enums.ManagedSmsEnums.*;
import com.messaging.managedsms.control.provisioning.ManagedSmsProviderAdapter.BindingContext;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class Msg91ManagedSmsProviderAdapterReadinessTests {

  private Msg91ManagedSmsProviderAdapter adapter;
  private ManagedSmsProviderBinding binding;
  private ManagedSmsTenant tenant;
  private ManagedSmsTemplateVersion version;
  private ManagedSmsHeader header;

  @BeforeEach
  void setUp() {
    adapter = new Msg91ManagedSmsProviderAdapter(new ManagedSmsProperties());

    tenant = new ManagedSmsTenant();
    tenant.setId(20L);
    tenant.setCompanyId(1L);
    tenant.setStatus(TenantStatus.PROVIDER_SYNCING);

    version = new ManagedSmsTemplateVersion();
    version.setId(30L);
    version.setCompanyId(1L);
    version.setDltStatus(DltTemplateStatus.APPROVED_DLT);
    version.setStatus(TemplateVersionStatus.APPROVED_DLT);
    version.setStaleAfter(Instant.now().plusSeconds(3600));

    header = new ManagedSmsHeader();
    header.setId(40L);
    header.setCompanyId(1L);
    header.setStatus(HeaderStatus.ACTIVE);
    header.setProviderMappingStatus(ProviderMappingStatus.READY);
    header.setStaleAfter(Instant.now().plusSeconds(3600));

    binding = new ManagedSmsProviderBinding();
    binding.setCompanyId(1L);
    binding.setTenantId(20L);
    binding.setTemplateVersionId(30L);
    binding.setHeaderId(40L);
    binding.setProvider(BroadcastSenderProvider.MSG91_SMS);
  }

  @Test
  void templateReferenceWithoutFlowIsNotRuntimeReady() {
    binding.setProviderTemplateRef("template-ref");

    assertThat(adapter.bindingReadiness(new BindingContext(binding, tenant, version, header)).ready())
        .isFalse();
  }

  @Test
  void flowReferenceWithConfirmedMappingIsRuntimeReady() {
    binding.setProviderFlowRef("flow-ref");

    assertThat(adapter.bindingReadiness(new BindingContext(binding, tenant, version, header)).ready())
        .isTrue();
  }

  @Test
  void unconfirmedHeaderMappingCannotBecomeRuntimeReady() {
    binding.setProviderFlowRef("flow-ref");
    header.setProviderMappingStatus(ProviderMappingStatus.ACTION_REQUIRED);

    assertThat(adapter.bindingReadiness(new BindingContext(binding, tenant, version, header)).ready())
        .isFalse();
  }

  @Test
  void foreignCompanyBindingCannotBecomeRuntimeReady() {
    binding.setProviderFlowRef("flow-ref");
    binding.setCompanyId(99L);

    assertThat(adapter.bindingReadiness(new BindingContext(binding, tenant, version, header)).ready())
        .isFalse();
  }
}
