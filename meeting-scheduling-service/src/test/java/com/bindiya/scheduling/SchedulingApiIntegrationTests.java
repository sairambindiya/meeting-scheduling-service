package com.bindiya.scheduling;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.docker.compose.enabled=false"
)
class SchedulingApiIntegrationTests {

    @Autowired
    private Environment environment;

    @Autowired
    private JdbcTemplate jdbc;

    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void cleanSchedulingData() {
        jdbc.update("DELETE FROM meeting_participants");
        jdbc.update("DELETE FROM meetings");
        jdbc.update("DELETE FROM time_slots");
    }

    @Test
    void createsUpdatesAndDeletesSlot() throws Exception {
        HttpResponse<String> created =
                createSlot("10:00:00", "10:30:00");

        assertEquals(201, created.statusCode());

        long slotId = onlySlotId();

        assertEquals("FREE", slotStatus(slotId));

        HttpResponse<String> updated = request(
                "PUT",
                "/users/1/slots/" + slotId,
                """
                {
                  "startTime": "2030-11-20T11:00:00Z",
                  "endTime": "2030-11-20T11:45:00Z",
                  "status": "BUSY"
                }
                """
        );

        assertEquals(200, updated.statusCode());
        assertEquals("BUSY", slotStatus(slotId));
        assertTrue(updated.body().contains("2030-11-20T11:45:00Z"));

        assertEquals(204, request(
                "DELETE", "/users/1/slots/" + slotId, null
        ).statusCode());

        assertEquals(404, request(
                "DELETE", "/users/1/slots/" + slotId, null
        ).statusCode());
    }

    @Test
    void rejectsOverlapButAllowsAdjacentSlots() throws Exception {
        assertEquals(201,
                createSlot("10:00:00", "10:30:00").statusCode());

        assertEquals(409,
                createSlot("10:15:00", "10:45:00").statusCode());

        assertEquals(201,
                createSlot("10:30:00", "11:00:00").statusCode());
    }

    @Test
    void aggregatesAdjacentFreeSlotsAndClipsToQueryRange()
            throws Exception {

        assertEquals(201,
                createSlot("10:00:00", "10:30:00").statusCode());

        assertEquals(201,
                createSlot("10:30:00", "11:00:00").statusCode());

        HttpResponse<String> response = request(
                "GET",
                "/users/1/availability"
                        + "?from=2030-11-20T10:15:00Z"
                        + "&to=2030-11-20T10:45:00Z"
                        + "&status=FREE",
                null
        );

        assertEquals(200, response.statusCode());

        // One startTime field means the two slots merged into one interval.
        assertEquals(1,
                response.body().split("\"startTime\"", -1).length - 1);

        assertTrue(response.body().contains(
                "\"startTime\":\"2030-11-20T10:15:00Z\""
        ));

        assertTrue(response.body().contains(
                "\"endTime\":\"2030-11-20T10:45:00Z\""
        ));

        HttpResponse<String> busy = request(
                "GET",
                "/users/1/availability"
                        + "?from=2030-11-20T00:00:00Z"
                        + "&to=2030-11-21T00:00:00Z"
                        + "&status=BUSY",
                null
        );

        assertEquals(200, busy.statusCode());
        assertTrue(busy.body().contains("\"intervals\":[]"));
    }

    @Test
    void booksSlotAndProtectsBookedSlot() throws Exception {
        assertEquals(201,
                createSlot("10:00:00", "10:30:00").statusCode());

        long slotId = onlySlotId();

        HttpResponse<String> booked = book(slotId);

        assertEquals(201, booked.statusCode());
        assertTrue(booked.body().contains("https://bindiya.com/meetings/"));
        assertEquals("BUSY", slotStatus(slotId));

        assertEquals(1L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM meetings WHERE slot_id = ?",
                Long.class,
                slotId
        ));

        // Owner plus the two requested participants.
        assertEquals(3L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM meeting_participants",
                Long.class
        ));

        assertEquals(409, book(slotId).statusCode());

        assertEquals(409, request(
                "DELETE", "/users/1/slots/" + slotId, null
        ).statusCode());

        assertEquals(409, request(
                "PUT",
                "/users/1/slots/" + slotId,
                """
                {
                  "startTime": "2030-11-20T10:00:00Z",
                  "endTime": "2030-11-20T10:30:00Z",
                  "status": "FREE"
                }
                """
        ).statusCode());
    }

    @Test
    void failedParticipantLookupDoesNotReserveSlot() throws Exception {
        assertEquals(201,
                createSlot("10:00:00", "10:30:00").statusCode());

        long slotId = onlySlotId();

        HttpResponse<String> response = request(
                "POST",
                "/users/1/slots/" + slotId + "/meeting",
                """
                {
                  "title": "Unknown participant",
                  "participantIds": [999999]
                }
                """
        );

        assertEquals(404, response.statusCode());
        assertEquals("FREE", slotStatus(slotId));

        assertEquals(0L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM meetings", Long.class
        ));
    }

    @Test
    void rejectsInvalidInputAndUnknownUser() throws Exception {
        assertEquals(400,
                createSlot("11:00:00", "10:00:00").statusCode());

        assertEquals(400, request(
                "POST", "/users/1/slots", "{}"
        ).statusCode());

        assertEquals(404, request(
                "POST",
                "/users/999999/slots",
                """
                {
                  "startTime": "2030-11-20T10:00:00Z",
                  "endTime": "2030-11-20T10:30:00Z"
                }
                """
        ).statusCode());
    }

    @Test
    void concurrentBookingsProduceOneSuccessAndOneConflict()
            throws Exception {

        assertEquals(201,
                createSlot("10:00:00", "10:30:00").statusCode());

        long slotId = onlySlotId();

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Start timed out");
                }
                return book(slotId).statusCode();
            });

            var second = executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Start timed out");
                }
                return book(slotId).statusCode();
            });

            try {
                assertTrue(ready.await(10, TimeUnit.SECONDS));
            } finally {
                start.countDown();
            }

            int firstStatus = first.get(30, TimeUnit.SECONDS);
            int secondStatus = second.get(30, TimeUnit.SECONDS);

            assertTrue(
                    (firstStatus == 201 && secondStatus == 409)
                            || (firstStatus == 409 && secondStatus == 201),
                    "Expected 201 and 409, got "
                            + firstStatus + " and " + secondStatus
            );
        }

        assertEquals(1L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM meetings WHERE slot_id = ?",
                Long.class,
                slotId
        ));

        assertEquals("BUSY", slotStatus(slotId));
    }

    private HttpResponse<String> createSlot(
            String start,
            String end
    ) throws Exception {
        return request(
                "POST",
                "/users/1/slots",
                """
                {
                  "startTime": "2030-11-20T%sZ",
                  "endTime": "2030-11-20T%sZ"
                }
                """.formatted(start, end)
        );
    }

    private HttpResponse<String> book(long slotId) throws Exception {
        return request(
                "POST",
                "/users/1/slots/" + slotId + "/meeting",
                """
                {
                  "title": "Project discussion",
                  "description": "Discuss next steps",
                  "participantIds": [2, 3]
                }
                """
        );
    }

    private long onlySlotId() {
        return jdbc.queryForObject(
                "SELECT id FROM time_slots",
                Long.class
        );
    }

    private String slotStatus(long slotId) {
        return jdbc.queryForObject(
                "SELECT status FROM time_slots WHERE id = ?",
                String.class,
                slotId
        );
    }

    private HttpResponse<String> request(
            String method,
            String path,
            String body
    ) throws Exception {
        String port = environment.getRequiredProperty("local.server.port");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .timeout(java.time.Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .method(
                        method,
                        body == null
                                ? HttpRequest.BodyPublishers.noBody()
                                : HttpRequest.BodyPublishers.ofString(body)
                )
                .build();

        return client.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );
    }
}