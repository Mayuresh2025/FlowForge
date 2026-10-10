
package com.flowforge.repository;

import com.flowforge.model.Job;
import com.flowforge.model.JobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public interface JobRepository extends JpaRepository<Job, UUID> {

    Page<Job> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<Job> findTop100ByStatusOrderByCreatedAtAsc(JobStatus status);

    long countByStatus(JobStatus status);

    /**
     * Atomically claims a queued job.
     * Only one worker can change QUEUED to RUNNING.
     */
    @Modifying
    @Transactional
    @Query("""
        update Job j
        set j.status = :running,
            j.attempts = j.attempts + 1,
            j.lastError = null,
            j.version = j.version + 1
        where j.id = :id
          and j.status = :queued
        """)
    int claimQueuedJob(
            @Param("id") UUID id,
            @Param("queued") JobStatus queued,
            @Param("running") JobStatus running
    );

    /**
     * Recovers jobs left RUNNING after an application restart.
     *
     * Jobs with attempts remaining return to QUEUED.
     * Jobs that exhausted their attempts become FAILED.
     */
    @Modifying
    @Transactional
    @Query("""
        update Job j
        set j.status = case
                when j.attempts < j.maxAttempts then :queued
                else :failed
            end,
            j.lastError = :message,
            j.version = j.version + 1
        where j.status = :running
        """)
    int recoverAbandonedRunningJobs(
            @Param("running") JobStatus running,
            @Param("queued") JobStatus queued,
            @Param("failed") JobStatus failed,
            @Param("message") String message
    );
}
