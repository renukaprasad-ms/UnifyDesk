package com.messaging.managedsms.delivery;

import com.messaging.common.exception.BadRequestException;
import com.messaging.managedsms.metareach.MetaReachDlrScheduler;
import com.messaging.managedsms.metareach.MetaReachProperties;
import com.messaging.managedsms.metareach.MetaReachSmsClient;
import com.messaging.managedsms.runtime.ManagedSmsRuntimeRepository;
import com.messaging.managedsms.runtime.ManagedSmsTemplateService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor
public class ManagedSmsProviderDispatch {
  private final JdbcTemplate jdbc;
  private final ManagedMsg91Gateway msg91;
  private final MetaReachSmsClient metaReach;
  private final MetaReachProperties metaConfig;
  private final MetaReachDlrScheduler dlrScheduler;

  public String requireProvider(Long companyId, Long runId) {
    List<String> matches=jdbc.queryForList(
      "select delivery_provider from managed_sms_run_bindings where company_id=? and run_id=?",
      String.class,companyId,runId);
    if (matches.size()!=1) throw new BadRequestException("Missing immutable managed SMS provider binding");
    String provider=matches.getFirst();
    if (!"MSG91_SMS".equals(provider) && !"METAREACH_SMS".equals(provider))
      throw new BadRequestException("Unsupported managed SMS provider");
    return provider;
  }
  public String accountKey(String provider,ManagedSmsRuntimeRepository.DeliveryRuntime runtime) {
    return "METAREACH_SMS".equals(provider) ? metaConfig.accountKey() : runtime.providerAccountKey();
  }
  public ManagedSmsSendResult send(
      String provider,ManagedSmsRuntimeRepository.DeliveryRuntime runtime,
      String destination,String body,Map<String,String> variables,
      List<ManagedSmsTemplateService.VariableSpec> specs,String correlation) {
    if ("METAREACH_SMS".equals(provider)) {
      return metaReach.send(runtime.headerSnapshot(),destination,body,runtime.dltTemplateIdSnapshot());
    }
    if ("MSG91_SMS".equals(provider)) {
      return msg91.send(runtime,destination,variables,specs,correlation);
    }
    throw new BadRequestException("Unsupported managed SMS provider");
  }
  public void afterAccepted(String provider,Long companyId,Long runId,Long recipientId,
      String correlation,String messageId,String destination) {
    if ("METAREACH_SMS".equals(provider))
      dlrScheduler.enqueue(companyId,runId,recipientId,correlation,messageId,destination);
  }
}
