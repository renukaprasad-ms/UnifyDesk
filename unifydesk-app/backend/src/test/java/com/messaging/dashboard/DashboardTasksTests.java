package com.messaging.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.concurrent.DelegatingSecurityContextExecutor;
import org.springframework.security.core.context.SecurityContextHolder;

class DashboardTasksTests {
  @Test
  void independentReadsStartInParallelWithTheRequestSecurityContext() throws Exception {
    try (var executor = Executors.newFixedThreadPool(2)) {
      var authentication = new UsernamePasswordAuthenticationToken("dashboard-user", "unused");
      SecurityContextHolder.getContext().setAuthentication(authentication);
      try {
        var tasks = new DashboardTasks(new DelegatingSecurityContextExecutor(executor), 5000);
        var unavailable = ConcurrentHashMap.<String>newKeySet();
        var started = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        java.util.function.Supplier<String> work =
            () -> {
              started.countDown();
              try {
                if (!release.await(3, TimeUnit.SECONDS))
                  throw new IllegalStateException("Reads did not overlap");
              } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
              }
              return SecurityContextHolder.getContext().getAuthentication().getName();
            };
        var first = tasks.load(1L, "first", true, work, unavailable);
        var second = tasks.load(1L, "second", true, work, unavailable);
        boolean parallel = started.await(2, TimeUnit.SECONDS);
        release.countDown();
        assertThat(parallel).isTrue();
        assertThat(first.get(2, TimeUnit.SECONDS)).isEqualTo("dashboard-user");
        assertThat(second.get(2, TimeUnit.SECONDS)).isEqualTo("dashboard-user");
        assertThat(unavailable).isEmpty();
      } finally {
        SecurityContextHolder.clearContext();
      }
    }
  }

  @Test
  void deniedReadsAreNeverScheduledAndFailuresDoNotBreakHealthyReads() {
    var tasks = new DashboardTasks(Runnable::run, 1000);
    var unavailable = ConcurrentHashMap.<String>newKeySet();
    var calls = new AtomicInteger();
    assertThat(tasks.load(1L, "denied", false, calls::incrementAndGet, unavailable).join())
        .isNull();
    assertThat(calls.get()).isZero();
    assertThat(
            tasks
                .load(
                    1L,
                    "failed",
                    true,
                    () -> {
                      throw new IllegalStateException("Database unavailable");
                    },
                    unavailable)
                .join())
        .isNull();
    assertThat(tasks.load(1L, "healthy", true, () -> 42, unavailable).join()).isEqualTo(42);
    assertThat(unavailable).containsExactly("failed");
  }

  @Test
  void queuedReadsTimeOutAndAreSkippedWhenAnExecutorEventuallyRunsThem() throws Exception {
    var queued = new AtomicReference<Runnable>();
    var tasks = new DashboardTasks(queued::set, 25);
    var unavailable = ConcurrentHashMap.<String>newKeySet();
    var calls = new AtomicInteger();
    assertThat(
            tasks
                .load(1L, "slow", true, calls::incrementAndGet, unavailable)
                .get(2, TimeUnit.SECONDS))
        .isNull();
    queued.get().run();
    assertThat(calls.get()).isZero();
    assertThat(unavailable).containsExactly("slow");
  }

  @Test
  void executorSaturationReturnsAnUnavailableSection() {
    var tasks =
        new DashboardTasks(
            work -> {
              throw new RejectedExecutionException();
            },
            1000);
    var unavailable = ConcurrentHashMap.<String>newKeySet();
    assertThat(tasks.load(1L, "busy", true, () -> 42, unavailable).join()).isNull();
    assertThat(unavailable).containsExactly("busy");
  }
}
