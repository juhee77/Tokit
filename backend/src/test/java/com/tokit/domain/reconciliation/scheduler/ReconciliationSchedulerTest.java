package com.tokit.domain.reconciliation.scheduler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.launch.JobLauncher;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReconciliationSchedulerTest {

    @Mock
    private JobLauncher jobLauncher;

    @Mock
    private Job reconciliationJob;

    @InjectMocks
    private ReconciliationScheduler scheduler;

    @Test
    @DisplayName("runReconciliationScheduled: 매일 새벽 3시 배치 스케줄러 트리거 시 Spring Batch reconciliationJob이 성공적으로 실행된다.")
    void runReconciliationScheduled_TriggersJobLauncher() throws Exception {
        // Given
        JobExecution jobExecution = new JobExecution(1L);
        jobExecution.setStatus(BatchStatus.COMPLETED);

        given(jobLauncher.run(eq(reconciliationJob), any(JobParameters.class)))
                .willReturn(jobExecution);

        // When
        scheduler.runReconciliationScheduled();

        // Then
        verify(jobLauncher).run(eq(reconciliationJob), any(JobParameters.class));
    }
}
