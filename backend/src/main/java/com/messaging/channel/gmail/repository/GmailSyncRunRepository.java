package com.messaging.channel.gmail.repository;

import com.messaging.channel.gmail.entity.GmailSyncRun;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GmailSyncRunRepository extends JpaRepository<GmailSyncRun, Long> {

  Optional<GmailSyncRun> findByJobId(Long jobId);
}
