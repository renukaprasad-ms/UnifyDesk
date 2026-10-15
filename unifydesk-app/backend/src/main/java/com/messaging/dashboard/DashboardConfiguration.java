package com.messaging.dashboard;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.task.DelegatingSecurityContextAsyncTaskExecutor;

@Configuration
public class DashboardConfiguration {
  @Bean
  public ThreadPoolTaskExecutor dashboardWorkerPool() {
    var executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(8);
    executor.setMaxPoolSize(8);
    executor.setQueueCapacity(64);
    executor.setThreadNamePrefix("dashboard-");
    return executor;
  }

  @Bean
  public AsyncTaskExecutor dashboardExecutor(ThreadPoolTaskExecutor dashboardWorkerPool) {
    return new DelegatingSecurityContextAsyncTaskExecutor(dashboardWorkerPool);
  }
}
