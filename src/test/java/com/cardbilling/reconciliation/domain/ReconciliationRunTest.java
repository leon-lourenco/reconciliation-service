package com.cardbilling.reconciliation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.cardbilling.reconciliation.domain.ReconciliationRun.Status;
import org.junit.jupiter.api.Test;

class ReconciliationRunTest {

    @Test
    void startsRunningWithNoCountsAndNoFinishTime() {
        ReconciliationRun run = ReconciliationRun.started();

        assertThat(run.getStatus()).isEqualTo(Status.RUNNING);
        assertThat(run.isInProgress()).isTrue();
        assertThat(run.getStartedAt()).isNotNull();
        assertThat(run.getFinishedAt()).isNull();
        assertThat(run.getTotalLines()).isZero();
    }

    @Test
    void completingRecordsTheTallyAndStopsTheClock() {
        ReconciliationRun run = ReconciliationRun.started();

        run.complete(10, 6, 3, 1);

        assertThat(run.getStatus()).isEqualTo(Status.COMPLETED);
        assertThat(run.isInProgress()).isFalse();
        assertThat(run.getTotalLines()).isEqualTo(10);
        assertThat(run.getMatchedCount()).isEqualTo(6);
        assertThat(run.getUnmatchedCount()).isEqualTo(3);
        assertThat(run.getDivergentCount()).isEqualTo(1);
        assertThat(run.getFinishedAt()).isNotNull();
    }

    @Test
    void failingStopsTheClockWithoutClaimingACompletedTally() {
        ReconciliationRun run = ReconciliationRun.started();

        run.fail();

        assertThat(run.getStatus()).isEqualTo(Status.FAILED);
        assertThat(run.isInProgress()).isFalse();
        assertThat(run.getFinishedAt()).isNotNull();
    }
}
