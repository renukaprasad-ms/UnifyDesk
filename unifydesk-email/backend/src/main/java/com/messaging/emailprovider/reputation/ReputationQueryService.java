package com.messaging.emailprovider.reputation;

import com.messaging.emailprovider.common.exception.BadRequestException;
import com.messaging.emailprovider.domain.repository.SendingDomainRepository;
import com.messaging.emailprovider.reputation.dto.ReputationDailyResponse;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReputationQueryService {

  private final ReputationDailyRepository repository;
  private final SendingDomainRepository domainRepository;

  public ReputationQueryService(
      ReputationDailyRepository repository, SendingDomainRepository domainRepository) {
    this.repository = repository;
    this.domainRepository = domainRepository;
  }

  @Transactional(readOnly = true)
  public List<ReputationDailyResponse> query(
      Long companyId, Long sendingDomainId, LocalDate from, LocalDate to) {
    if (from.isAfter(to)) {
      throw new BadRequestException("from must be on or before to");
    }
    if (from.plusDays(366).isBefore(to)) {
      throw new BadRequestException("Reputation query range must not exceed 366 days");
    }

    if (sendingDomainId != null
        && domainRepository.findByIdAndCompanyId(sendingDomainId, companyId).isEmpty()) {
      throw new BadRequestException("Sending domain does not belong to company");
    }

    List<ReputationDaily> rows =
        sendingDomainId == null
            ? repository.findAllByCompanyIdAndMetricDateBetweenOrderByMetricDateAsc(
                companyId, from, to)
            : repository
                .findAllByCompanyIdAndSendingDomainIdAndMetricDateBetweenOrderByMetricDateAsc(
                    companyId, sendingDomainId, from, to);

    return rows.stream().map(ReputationDailyResponse::from).toList();
  }
}
