package com.flowforge.dto;

import com.flowforge.model.Job;
import com.flowforge.model.JobStatus;
import java.time.Instant;
import java.util.UUID;

public record JobResponse(UUID id, String type, String payload, JobStatus status, int attempts, int maxAttempts,
                          String result, String lastError, Instant createdAt, Instant updatedAt) {
    public static JobResponse from(Job j) { return new JobResponse(j.getId(),j.getType(),j.getPayload(),j.getStatus(),j.getAttempts(),j.getMaxAttempts(),j.getResult(),j.getLastError(),j.getCreatedAt(),j.getUpdatedAt()); }
}
