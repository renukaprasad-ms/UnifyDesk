package com.messaging.emailprovider.mailbox.outbound;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/** Atomic mailbox-hour and organization-day budgets; Redis errors fail closed. */
@Component
@ConditionalOnProperty(prefix="app.mail.outbound",name="enabled",havingValue="true")
public class MailboxSendingRateLimiter {
  private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>(
      """
      local a=tonumber(redis.call('GET',KEYS[1]) or '0')
      local b=tonumber(redis.call('GET',KEYS[2]) or '0')
      if a>=tonumber(ARGV[1]) or b>=tonumber(ARGV[2]) then return 0 end
      if redis.call('INCR',KEYS[1])==1 then redis.call('EXPIRE',KEYS[1],3600) end
      if redis.call('INCR',KEYS[2])==1 then redis.call('EXPIRE',KEYS[2],86400) end
      return 1
      """,Long.class);
  private final StringRedisTemplate redis;
  public MailboxSendingRateLimiter(StringRedisTemplate redis) { this.redis=redis; }

  public boolean permit(long organizationId,long mailboxId) {
    try {
      Long result=redis.execute(SCRIPT,List.of(
          "ud:mail:hour:"+organizationId+":"+mailboxId,
          "ud:mail:day:"+organizationId),"20","100");
      return Long.valueOf(1).equals(result);
    } catch (RuntimeException unavailable) {
      return false;
    }
  }
}
