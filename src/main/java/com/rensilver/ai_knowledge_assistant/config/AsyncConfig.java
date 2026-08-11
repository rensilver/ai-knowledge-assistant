package com.rensilver.ai_knowledge_assistant.config;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Backs {@code @Async} document ingestion ({@code DocumentIngestionService})
 * with a small, bounded pool rather than Spring's default
 * {@code SimpleAsyncTaskExecutor} (unbounded thread-per-task) — ingestion is
 * CPU/IO bound per document, not something that should scale threads 1:1
 * with concurrent uploads.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "documentIngestionExecutor")
    public Executor documentIngestionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("doc-ingest-");
        // MDC is thread-local, so the requestId CorrelationIdFilter set on the
        // upload request thread wouldn't otherwise show up in this pool's log
        // lines. Copy it across the async boundary and clear it again after,
        // since the thread gets reused for the next document.
        executor.setTaskDecorator(runnable -> {
            Map<String, String> callerContext = MDC.getCopyOfContextMap();
            return () -> {
                try {
                    if (callerContext != null) {
                        MDC.setContextMap(callerContext);
                    }
                    runnable.run();
                } finally {
                    MDC.clear();
                }
            };
        });
        executor.initialize();
        return executor;
    }
}
