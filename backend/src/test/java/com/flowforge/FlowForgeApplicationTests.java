package com.flowforge;

import com.flowforge.model.Job;
import com.flowforge.model.JobStatus;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FlowForgeApplicationTests {
    @Test void failedJobIsQueuedUntilAttemptLimitIsReached() {
        Job job = new Job("FAIL", "{}", 2);
        assertEquals(JobStatus.QUEUED, job.getStatus());
        job.markRunning();
        assertEquals(1, job.getAttempts());
        job.fail("first failure");
        assertEquals(JobStatus.QUEUED, job.getStatus());
        job.markRunning();
        job.fail("second failure");
        assertEquals(JobStatus.FAILED, job.getStatus());
        assertEquals(2, job.getAttempts());
    }

    @Test void successfulJobStoresItsResult() {
        Job job = new Job("ECHO", "{\"message\":\"hi\"}", 3);
        job.markRunning();
        job.succeed("done");
        assertEquals(JobStatus.SUCCEEDED, job.getStatus());
        assertEquals("done", job.getResult());
        assertNull(job.getLastError());
    }
}
