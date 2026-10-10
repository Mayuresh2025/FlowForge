
package com.flowforge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowforge.model.Job;
import com.flowforge.model.JobStatus;
import com.flowforge.repository.JobRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Component
public class JobWorker {

    private final JobRepository repository;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    private final ExecutorService workers =
            Executors.newFixedThreadPool(2);

    private volatile boolean running = true;

    public JobWorker(
            JobRepository repository,
            StringRedisTemplate redis,
            ObjectMapper mapper
    ) {
        this.repository = repository;
        this.redis = redis;
        this.mapper = mapper;
    }

    /**
     * Recover abandoned jobs before starting worker threads.
     */
    @PostConstruct
    public void start() {
        int recovered = repository.recoverAbandonedRunningJobs(
                JobStatus.RUNNING,
                JobStatus.QUEUED,
                JobStatus.FAILED,
                "Previous execution was interrupted by an application restart"
        );

        System.out.println(
                "Crash recovery completed. Recovered "
                        + recovered + " abandoned job(s)."
        );

        for (int i = 0; i < 2; i++) {
            workers.submit(this::consume);
        }
    }

    @PreDestroy
    public void stop() {
        running = false;
        workers.shutdownNow();
    }

    /**
     * Continuously consume job IDs from Redis.
     */
    private void consume() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                String id = redis.opsForList().rightPop(
                        QueueDispatcher.QUEUE_KEY,
                        Duration.ofSeconds(2)
                );

                if (id == null) {
                    continue;
                }

                try {
                    execute(UUID.fromString(id));
                } catch (IllegalArgumentException e) {
                    System.err.println("Invalid job ID: " + id);
                } catch (Exception e) {
                    System.err.println("Job processing error: " + id);
                    e.printStackTrace(System.err);
                }

            } catch (Exception e) {
                if (!running || Thread.currentThread().isInterrupted()) {
                    break;
                }

                System.err.println("Worker loop error:");
                e.printStackTrace(System.err);

                try {
                    TimeUnit.MILLISECONDS.sleep(500);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    /**
     * Atomically claim and execute a job.
     */
    public void execute(UUID id) {

        int claimed = repository.claimQueuedJob(
                id,
                JobStatus.QUEUED,
                JobStatus.RUNNING
        );

        // Reject duplicate messages and jobs already claimed.
        if (claimed == 0) {
            return;
        }

        // Load the entity after the atomic claim.
        Job job = repository.findById(id).orElse(null);

        if (job == null) {
            System.err.println(
                    "Claimed job could not be loaded: " + id
            );
            return;
        }

        try {
            String result = runTask(job);
            job.succeed(result);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            job.fail("Worker interrupted during execution");

        } catch (Exception e) {
            String message = e.getMessage();

            job.fail(
                    message == null || message.isBlank()
                            ? "Job failed: " + e.getClass().getSimpleName()
                            : message
            );

        } finally {
            repository.saveAndFlush(job);
        }
    }

    /**
     * Execute the supported demonstration job types.
     */
    private String runTask(Job job) throws Exception {
        JsonNode payload = mapper.readTree(job.getPayload());

        return switch (job.getType()) {

            case "ECHO" ->
                    mapper.writeValueAsString(payload);

            case "WAIT" -> {
                long milliseconds =
                        payload.path("milliseconds").asLong(1000);

                if (milliseconds < 0 || milliseconds > 30000) {
                    throw new IllegalArgumentException(
                            "milliseconds must be between 0 and 30000"
                    );
                }

                Thread.sleep(milliseconds);

                yield "Completed wait of " + milliseconds + " ms";
            }

            case "FAIL" ->
                    throw new IllegalStateException(
                            payload.path("message").asText(
                                    "Intentional failure for retry testing"
                            )
                    );

            default ->
                    throw new IllegalArgumentException(
                            "Unsupported job type: " + job.getType()
                    );
        };
    }
}
