package com.messaging.dashboard;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class DashboardTasks {
  private final Executor executor;
  private final long timeoutMillis;

  public DashboardTasks(
      @Qualifier("dashboardExecutor") Executor executor,
      @Value("${app.dashboard.timeout-millis:10000}") long timeoutMillis) {
    if (timeoutMillis <= 0)
      throw new IllegalArgumentException("Dashboard timeout must be positive");
    this.executor = executor;
    this.timeoutMillis = timeoutMillis;
  }

  public <T> CompletableFuture<T> load(
      Long companyId,
      String section,
      boolean allowed,
      Supplier<T> work,
      java.util.Set<String> unavailable) {
    if (!allowed) return CompletableFuture.completedFuture(null);
    var result = new CompletableFuture<T>();
    try {
      executor.execute(
          () -> {
            // Expired queued work must not consume a database connection later.
            if (result.isDone()) return;
            try {
              result.complete(work.get());
            } catch (Exception exception) {
              result.completeExceptionally(exception);
            }
          });
    } catch (RuntimeException exception) {
      result.completeExceptionally(exception);
    }
    return result
        .orTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
        .exceptionally(
            exception -> {
              unavailable.add(section);
              log.warn(
                  "Dashboard section {} unavailable for company {}", section, companyId, exception);
              return null;
            });
  }
}
