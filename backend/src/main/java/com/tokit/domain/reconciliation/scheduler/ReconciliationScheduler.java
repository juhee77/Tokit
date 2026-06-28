package com.tokit.domain.reconciliation.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class ReconciliationScheduler {

    private final JobLauncher jobLauncher;
    private final Job reconciliationJob;

    // 매일 새벽 3시에 배치 실행 (Cron: 0 0 3 * * ?)
    @Scheduled(cron = "0 0 3 * * ?")
    public void runReconciliationScheduled() {
        log.info("[Reconciliation Scheduler] Starting scheduled daily on-off chain balance audit...");
        try {
            JobParameters params = new JobParametersBuilder()
                    .addLong("time", System.currentTimeMillis())
                    .toJobParameters();
            var execution = jobLauncher.run(reconciliationJob, params);
            log.info("[Reconciliation Scheduler] Scheduled batch completed with status: {}", execution.getStatus());
        } catch (Exception e) {
            log.error("[Reconciliation Scheduler] CRITICAL: Failed to execute scheduled reconciliation job!", e);
        }
    }
}
