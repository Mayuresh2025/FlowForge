
package com.flowforge;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class FlowForgeIntegrationIT {

    private static final String BASE_URL =
            System.getProperty(
                    "flowforge.base-url",
                    "http://localhost:8080"
            );

    private static HttpClient client;

    @BeforeAll
    static void setUp() {
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    private HttpResponse<String> send(
            String method,
            String path,
            String body
    ) throws Exception {

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json");

        switch (method) {
            case "GET" -> builder.GET();
            case "POST" -> builder.POST(
                    HttpRequest.BodyPublishers.ofString(
                            body == null ? "" : body
                    )
            );
            default -> throw new IllegalArgumentException(
                    "Unsupported method: " + method
            );
        }

        return client.send(
                builder.build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private String createJob(
            String type,
            String payload,
            int maxAttempts
    ) throws Exception {

        String body = """
                {
                  "type": "%s",
                  "payload": {"value": "%s"},
                  "maxAttempts": %d
                }
                """.formatted(type, payload, maxAttempts);

        HttpResponse<String> response =
                send("POST", "/api/jobs", body);

        assertEquals(
                201,
                response.statusCode(),
                "Job creation failed: " + response.body()
        );

        return response.body();
    }

    private String extractId(String json) {
        var matcher = Pattern
                .compile("\"id\"\\s*:\\s*\"([^\"]+)\"")
                .matcher(json);

        assertTrue(
                matcher.find(),
                "No job ID found in response: " + json
        );

        return matcher.group(1);
    }

    private String waitForTerminalStatus(
            String id,
            Duration timeout
    ) throws Exception {

        long deadline = System.nanoTime() + timeout.toNanos();

        while (System.nanoTime() < deadline) {
            HttpResponse<String> response =
                    send("GET", "/api/jobs/" + id, null);

            assertEquals(
                    200,
                    response.statusCode(),
                    "Could not retrieve job: " + response.body()
            );

            String json = response.body();

            for (String status : new String[]{
                    "SUCCEEDED", "FAILED", "CANCELLED"
            }) {
                if (Pattern.compile(
                        "\"status\"\\s*:\\s*\"" + status + "\""
                ).matcher(json).find()) {
                    return status;
                }
            }

            Thread.sleep(300);
        }

        fail("Job did not reach a terminal status before timeout. ID: " + id);
        return "";
    }

    private String waitForStatus(
            String id,
            String expectedStatus,
            Duration timeout
    ) throws Exception {

        long deadline = System.nanoTime() + timeout.toNanos();

        while (System.nanoTime() < deadline) {
            HttpResponse<String> response =
                    send("GET", "/api/jobs/" + id, null);

            assertEquals(
                    200,
                    response.statusCode(),
                    "Job retrieval failed: " + response.body()
            );

            String json = response.body();

            if (Pattern.compile(
                    "\"status\"\\s*:\\s*\"" + expectedStatus + "\""
            ).matcher(json).find()) {
                return json;
            }

            Thread.sleep(100);
        }

        fail("Job " + id + " did not reach status " + expectedStatus);
        return "";
    }

    @Test
    void echoJobShouldSucceed() throws Exception {
        String id = extractId(
                createJob("ECHO", "integration-test", 3)
        );

        assertEquals(
                "SUCCEEDED",
                waitForTerminalStatus(id, Duration.ofSeconds(20))
        );
    }

    @Test
    void waitJobShouldSucceed() throws Exception {
        String id = extractId(
                createJob("WAIT", "integration-test", 3)
        );

        assertEquals(
                "SUCCEEDED",
                waitForTerminalStatus(id, Duration.ofSeconds(20))
        );
    }

    @Test
    void failJobShouldExhaustRetries() throws Exception {
        String id = extractId(
                createJob("FAIL", "integration-test", 3)
        );

        assertEquals(
                "FAILED",
                waitForTerminalStatus(id, Duration.ofSeconds(30))
        );

        HttpResponse<String> response =
                send("GET", "/api/jobs/" + id, null);

        assertEquals(200, response.statusCode());

        assertTrue(
                Pattern.compile("\"attempts\"\\s*:\\s*3")
                        .matcher(response.body()).find(),
                "Expected 3 attempts: " + response.body()
        );
    }

    @Test
    void unsupportedJobTypeShouldBeRejected() throws Exception {
        String body = """
                {
                  "type": "UNKNOWN",
                  "payload": {"value": "test"},
                  "maxAttempts": 3
                }
                """;

        HttpResponse<String> response =
                send("POST", "/api/jobs", body);

        assertTrue(
                response.statusCode() >= 400
                        && response.statusCode() < 500,
                "Expected 4xx but received "
                        + response.statusCode()
                        + ": " + response.body()
        );
    }

    @Test
    void createdJobShouldBeRetrievable() throws Exception {
        String id = extractId(
                createJob("ECHO", "retrieval-test", 3)
        );

        HttpResponse<String> response =
                send("GET", "/api/jobs/" + id, null);

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains(id));
    }

    @Test
    void statsEndpointShouldRespondSuccessfully() throws Exception {
        HttpResponse<String> response =
                send("GET", "/api/stats", null);

        assertEquals(
                200,
                response.statusCode(),
                response.body()
        );
    }

    @Test
    void queuedJobShouldBeCancelledWithoutExecution() throws Exception {
        String id = extractId(
                createJob("WAIT", "cancellation-test", 3)
        );

        HttpResponse<String> cancelResponse =
                send("POST", "/api/jobs/" + id + "/cancel", "{}");

        if (cancelResponse.statusCode() == 409) {
            // The worker claimed the job before cancellation.
            // Verify that it was not incorrectly marked cancelled.
            HttpResponse<String> current =
                    send("GET", "/api/jobs/" + id, null);

            assertEquals(200, current.statusCode());

            assertFalse(
                    Pattern.compile("\"status\"\\s*:\\s*\"CANCELLED\"")
                            .matcher(current.body()).find(),
                    "A claimed job must not be marked cancelled"
            );

            return;
        }

        assertEquals(
                200,
                cancelResponse.statusCode(),
                cancelResponse.body()
        );

        String finalJob = waitForStatus(
                id,
                "CANCELLED",
                Duration.ofSeconds(5)
        );

        assertTrue(
                Pattern.compile("\"attempts\"\\s*:\\s*0")
                        .matcher(finalJob).find(),
                "Cancelled job should have zero attempts: " + finalJob
        );
    }

    @Test
    void runningJobCancellationShouldReturnConflict() throws Exception {
        String body = """
                {
                  "type": "WAIT",
                  "payload": {"milliseconds": 5000},
                  "maxAttempts": 3
                }
                """;

        HttpResponse<String> createResponse =
                send("POST", "/api/jobs", body);

        assertEquals(
                201,
                createResponse.statusCode(),
                "Job creation failed: " + createResponse.body()
        );

        String id = extractId(createResponse.body());

        waitForStatus(
                id,
                "RUNNING",
                Duration.ofSeconds(10)
        );

        HttpResponse<String> response =
                send("POST", "/api/jobs/" + id + "/cancel", "{}");

        assertEquals(
                409,
                response.statusCode(),
                "Running jobs must not be cancellable: "
                        + response.body()
        );
    }
}
