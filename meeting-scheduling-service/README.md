# Meeting Scheduling Service

A mini Doodle backend for managing availability and scheduling meetings.

Built with Java 21, Spring Boot, PostgreSQL and Flyway.

## Run locally

Prerequisites:
- Git
- Docker with Docker Compose

Clone the repository and start the application:

```bash
git clone --branch feat/scheduling https://github.com/sairambindiya/meeting-scheduling-service.git
cd meeting-scheduling-service
docker compose up --build -d
```

Docker builds the application and starts PostgreSQL. Java and Maven are not required on the host to run it.

Check application health:

```bash
curl http://localhost:8080/actuator/health
```

View logs:

```bash
docker compose logs -f app
```

Stop the application:

```bash
docker compose down
```

Database data persists between restarts. To delete local data and start fresh:

```bash
docker compose down -v
docker compose up --build -d
```

**Warning:** `down -v` deletes all data in the project's database volume.

## Sample users

On a fresh database, migrations create:

| User ID | Name |
|---|---|
| 1 | Bindu Murulidhar |
| 2 | Sindhu Kiran |
| 3 | Spoorthi Ganesh |

Each user has an internal calendar. User registration is outside the scope of this implementation.

## API endpoints

Base URL: `http://localhost:8080`

The `userId` identifies the owner of the slots being managed or booked.

| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/users/{userId}/slots` | Create a free slot |
| PUT | `/users/{userId}/slots/{slotId}` | Update time and free/busy status |
| DELETE | `/users/{userId}/slots/{slotId}` | Delete an unbooked slot |
| GET | `/users/{userId}/slots` | List slots overlapping a time range |
| GET | `/users/{userId}/availability` | Get aggregated availability |
| POST | `/users/{userId}/slots/{slotId}/meeting` | Book a free slot |

Both GET endpoints require `from` and `to`. An optional `status` parameter accepts `FREE` or `BUSY`.

Slot listing also accepts `page` (default `0`) and `size`
(default `20`, maximum `100`). Pages are zero-based.
Results are sorted by start time, then slot ID.
Status filtering is applied before pagination.

Both GET endpoints limit the requested time range to 31 days.
Aggregated availability is not paginated.
Invalid pagination or time ranges return `400 Bad Request`.

## Example workflow

### 1. Create a slot

```bash
curl -i -X POST 'http://localhost:8080/users/1/slots' \
  -H 'Content-Type: application/json' \
  -d '{
    "startTime": "2030-11-20T10:00:00Z",
    "endTime": "2030-11-20T10:30:00Z"
  }'
```

Expected: `201 Created`. Use the returned slot ID in subsequent requests.

### 2. List available slots

```bash
curl 'http://localhost:8080/users/1/slots?from=2030-11-20T00:00:00Z&to=2030-11-21T00:00:00Z&status=FREE&page=0&size=20'
```

Slots are returned inside `content`, alongside pagination metadata:

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0
}
```

This example represents an empty result.

### 3. Book a meeting

Replace `SLOT_ID` with the ID returned when creating the slot.

```bash
curl -i -X POST 'http://localhost:8080/users/1/slots/SLOT_ID/meeting' \
  -H 'Content-Type: application/json' \
  -d '{
    "title": "Project discussion",
    "description": "Discuss next steps",
    "participantIds": [2, 3]
  }'
```

Expected: `201 Created`, with meeting details and a placeholder URL.

Repeating the booking returns `409 Conflict`.

### 4. Query aggregated availability

```bash
curl 'http://localhost:8080/users/1/availability?from=2030-11-20T00:00:00Z&to=2030-11-21T00:00:00Z'
```

### 5. Update an unbooked slot

```bash
curl -i -X PUT 'http://localhost:8080/users/1/slots/SLOT_ID' \
  -H 'Content-Type: application/json' \
  -d '{
    "startTime": "2030-11-20T11:00:00Z",
    "endTime": "2030-11-20T11:45:00Z",
    "status": "BUSY"
  }'
```

All fields are required. Updating a booked slot returns `409 Conflict`.

### 6. Delete an unbooked slot

```bash
curl -i -X DELETE \
  'http://localhost:8080/users/1/slots/SLOT_ID'
```

Expected: `204 No Content`. Deleting a booked slot returns `409 Conflict`.

## Scheduling rules and assumptions

- Start and end timestamps define any positive slot duration.
- Send timestamps in ISO-8601 format with an offset; examples use UTC (`Z`).
- Slots cannot overlap within the same calendar. Adjacent slots are allowed.
- Time ranges use an inclusive start and exclusive end.
- Booking reserves the entire selected slot.
- A manually busy slot cannot be booked.
- Booked slots cannot be edited or deleted through slot management.
- The slot owner is automatically included as a participant.
- Participants must be existing users.
- Booking reserves only the slot owner's calendar; participant calendars are not checked or reserved.
- Availability merges adjacent intervals with the same status and clips them to the requested range.
- Time outside explicitly created slots is unspecified, not implicitly free.
- Authentication, notifications and video-call integration are outside scope.
- Returned `https://bindiya.com/meetings/{id}` URLs are placeholders, not functional meeting rooms.

## Concurrency and persistence

Slot changes and bookings acquire a PostgreSQL pessimistic lock on the owner's calendar, followed by the slot where applicable. This serializes changes within one calendar while allowing different calendars to be updated independently.

Meeting creation and the slot's transition to `BUSY` happen in one transaction. A unique database constraint also prevents multiple meetings from referencing the same slot.

Flyway manages schema changes. Hibernate validates entity mappings against the database.

## Error responses

| Status | Meaning |
|---|---|
| 400 | Invalid request, timestamps or time range |
| 404 | User calendar, slot or participant not found |
| 409 | Overlap, unavailable slot or modification of a booked slot |

Errors use Spring's Problem Detail response format.

## Tests

Running tests locally requires Java 21 and Docker. Maven is provided through the wrapper.
- Pagination with status filtering and chronological ordering
- Rejection of invalid page sizes and negative page numbers
- Enforcement of the 31-day query limit
- 
```bash
./mvnw clean verify
```

Tests use an isolated PostgreSQL 17 Testcontainer.

Integration tests cover:
- Slot creation, update and deletion
- Overlap rejection and adjacent slots
- Availability aggregation and filtering
- Meeting booking and protection of booked slots
- Invalid participants and transaction rollback
- Invalid input and unknown users
- Concurrent bookings: one success and one conflict

## Health and metrics

```text
GET /actuator/health
```

Actuator is included for health checks and metrics support. Additional management endpoints are not exposed by this setup.