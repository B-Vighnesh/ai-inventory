package com.stockpulse.config;

import org.springframework.context.annotation.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.time.Clock;

@Configuration
public class CommerceConfig {
    @Bean
    Clock clock() { return Clock.systemUTC(); }

    @Bean(name = "recommendationExecutor")
    ThreadPoolTaskExecutor recommendationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(128);
        executor.setThreadNamePrefix("commerce-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(25);
        return executor;
    }
}
