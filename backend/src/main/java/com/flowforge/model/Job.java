
package com.flowforge.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "jobs",
    indexes = {
        @Index(
            name = "idx_jobs_status_created",
            columnList = "status, created_at"
        )
    }
)
public class Job {

    @Id
    private UUID id;

    @Column(nullable = false, length = 40)
    private String type;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false)
    private int maxAttempts;

    @Column(columnDefinition = "text")
    private String result;

    @Column(columnDefinition = "text")
    private String lastError;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected Job() {
        // Required by JPA
    }

    public Job(String type, String payload, int maxAttempts) {
        this.id = UUID.randomUUID();
        this.type = type;
        this.payload = payload;
        this.status = JobStatus.QUEUED;
        this.attempts = 0;
        this.maxAttempts = maxAttempts;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public String getPayload() {
        return payload;
    }

    public JobStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public String getResult() {
        return result;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void markRunning() {
        this.status = JobStatus.RUNNING;
        this.attempts++;
        this.lastError = null;
    }

    public void succeed(String value) {
        this.status = JobStatus.SUCCEEDED;
        this.result = value;
        this.lastError = null;
    }

    public void fail(String message) {
        this.lastError = message;

        if (this.attempts < this.maxAttempts) {
            this.status = JobStatus.QUEUED;
        } else {
            this.status = JobStatus.FAILED;
        }
    }

    public void cancel() {
        this.status = JobStatus.CANCELLED;
    }
}
