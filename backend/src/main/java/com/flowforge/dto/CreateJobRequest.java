package com.flowforge.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record CreateJobRequest(
    @NotBlank @Size(max=40) String type,
    @NotNull Map<String,Object> payload,
    @Min(1) @Max(5) Integer maxAttempts
) {}
