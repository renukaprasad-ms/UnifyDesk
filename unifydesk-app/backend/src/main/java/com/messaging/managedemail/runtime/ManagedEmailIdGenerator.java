package com.messaging.managedemail.runtime;

import com.messaging.common.id.IdGenerator;
import org.springframework.stereotype.Component;

@Component
public class ManagedEmailIdGenerator {

  public long nextId() {
    return IdGenerator.nextId();
  }
}
