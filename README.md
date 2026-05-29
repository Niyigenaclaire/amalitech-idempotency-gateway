# Idempotency Gateway — The "Pay-Once" Protocol

A Spring Boot REST API that ensures payment requests are processed **exactly once**, no matter how many times a client retries. Built for FinSafe Transactions Ltd. to eliminate double-charging caused by network timeouts and client retries.

---

## Architecture Diagram

```
Client (e-commerce shop)
        |
        | POST /process-payment
        | Header: Idempotency-Key: <unique-key>
        | Body: { "amount": 100, "currency": "GHS" }
        |
        v
+-------------------------+
|   Idempotency Gateway   |
|                         |
|  1. Extract key + hash  |
|     request body        |
|                         |
|  2. Check in-memory     |
|     store (ConcurrentHashMap)
|                         |
|     KEY NOT FOUND?      |
|     --> Mark IN_FLIGHT  |
|     --> Process payment |
|         (2s delay)      |
|     --> Cache response  |
|     --> Mark COMPLETED  |
|     --> Return 201      |
|                         |
|     KEY FOUND +         |
|     SAME BODY?          |
|     --> If IN_FLIGHT:   |
|         wait/block      |
|     --> If COMPLETED:   |
|         return cached   |
|         X-Cache-Hit:true|
|                         |
|     KEY FOUND +         |
|     DIFFERENT BODY?     |
|     --> Return 422      |
|                         |
+-------------------------+
        |
        v
   Response to Client
```

### Sequence Diagram

```
Client          Gateway         Store
  |                |               |
  |--POST /pay---->|               |
  |  Key: abc-123  |               |
  |                |--putIfAbsent->|
  |                |<--null (new)--|
  |                |               |
  |                |  [2s delay]   |
  |                |               |
  |                |--cache resp-->|
  |<--201 Created--|               |
  |                |               |
  |--POST /pay---->|  (retry)      |
  |  Key: abc-123  |               |
  |                |--lookup------>|
  |                |<--HIT---------|
  |<--201 + X-Cache-Hit: true      |
  |                |               |
```

---

## Tech Stack

- **Java 21**
- **Spring Boot 3.2.5**
- **Maven** (build tool)
- **ConcurrentHashMap** (in-memory idempotency store)
- **JUnit 5** (tests)

---

## Setup & Running

### Prerequisites

- Java 21+
- Maven 3.8+ (or use the included `mvnw` wrapper)

### Clone & Run

```bash
git clone https://github.com/Niyigenaclaire/amalitech-idempotency-gateway.git
cd amalitech-idempotency-gateway
mvn spring-boot:run
```

The server starts on **http://localhost:8080**

### Run Tests

```bash
mvn test
```

### Build JAR

```bash
mvn clean package
java -jar target/idempotency-gateway-1.0.0.jar
```

---

## API Documentation

### POST `/process-payment`

Process a payment with idempotency protection.

**Required Header:**
| Header | Description |
|--------|-------------|
| `Idempotency-Key` | A unique string (UUID recommended) identifying this request |

**Request Body:**
```json
{
  "amount": 100,
  "currency": "GHS"
}
```

**Responses:**

| Scenario | Status | Headers |
|----------|--------|---------|
| First request (new key) | `201 Created` | `X-Cache-Hit: false` |
| Duplicate request (same key + same body) | `201 Created` | `X-Cache-Hit: true` |
| Conflict (same key + different body) | `422 Unprocessable Entity` | — |
| Missing header | `400 Bad Request` | — |

---

### Example Requests

#### 1. First Payment (Happy Path)

```bash
curl -X POST http://localhost:8080/process-payment \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: pay-uuid-001" \
  -d '{"amount": 100, "currency": "GHS"}'
```

**Response (201 Created):**
```json
{
  "status": "SUCCESS",
  "message": "Charged 100 GHS",
  "transactionId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "processedAt": "2026-05-29T10:00:00Z"
}
```

---

#### 2. Duplicate Request (Idempotency)

```bash
curl -X POST http://localhost:8080/process-payment \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: pay-uuid-001" \
  -d '{"amount": 100, "currency": "GHS"}'
```

**Response (201 Created) — same body, no re-processing:**
```json
{
  "status": "SUCCESS",
  "message": "Charged 100 GHS",
  "transactionId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "processedAt": "2026-05-29T10:00:00Z"
}
```
Response header: `X-Cache-Hit: true`

---

#### 3. Conflict — Same Key, Different Body

```bash
curl -X POST http://localhost:8080/process-payment \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: pay-uuid-001" \
  -d '{"amount": 500, "currency": "GHS"}'
```

**Response (422 Unprocessable Entity):**
```json
{
  "error": "Idempotency key already used for a different request body.",
  "status": 422,
  "timestamp": "2026-05-29T10:00:05Z"
}
```

---

### GET `/gateway/stats`

Returns current gateway statistics (Developer's Choice feature).

```bash
curl http://localhost:8080/gateway/stats
```

**Response (200 OK):**
```json
{
  "cachedKeys": 5,
  "serverStartedAt": "2026-05-29T09:00:00Z",
  "currentTime": "2026-05-29T10:30:00Z",
  "status": "UP"
}
```

---

### GET `/actuator/health`

Spring Boot health check endpoint.

```bash
curl http://localhost:8080/actuator/health
```

---

## Design Decisions

### 1. ConcurrentHashMap + `putIfAbsent` for Race Condition Safety

The core of the idempotency logic uses `ConcurrentHashMap.putIfAbsent()`. This is an atomic operation — it inserts a new entry only if the key is absent, and returns the existing entry if it's already there. This eliminates the classic check-then-act race condition without needing explicit locks on the map itself.

### 2. IN_FLIGHT State + `synchronized` + `wait/notifyAll`

When two identical requests arrive simultaneously (same key, same body), the second request must not start a new payment process. Instead, it waits using Java's `synchronized` + `wait()` on the shared `CachedEntry` object. When the first request finishes, it calls `notifyAll()` to wake up all waiting threads. This correctly handles the bonus "in-flight" race condition scenario.

### 3. SHA-256 Body Hashing

Rather than storing the full request body string, the request is hashed with SHA-256. This keeps memory usage low and provides a consistent, collision-resistant fingerprint for body comparison.

### 4. In-Memory Store (No External Dependencies)

The store uses a plain `ConcurrentHashMap`. This means zero external dependencies (no Redis, no database) — the server starts immediately with `mvn spring-boot:run`. In a production system, this would be replaced with Redis for distributed deployments.

---

## Developer's Choice Feature: Gateway Stats Endpoint

**Endpoint:** `GET /gateway/stats`

**Why I added it:**

In a real Fintech environment, the idempotency store grows over time as more payment keys are cached. Without visibility into this, operations teams have no way to:
- Detect memory pressure before it causes outages
- Know how many unique transactions have been processed
- Confirm the service is running without accessing internal infrastructure

The `/gateway/stats` endpoint exposes the current cache size, server start time, and status in a single lightweight call. This is the kind of operational observability feature that separates a production-ready service from a prototype.

---

## Pre-Submission Checklist

- [x] Repository is **Public**
- [x] No `node_modules`, `.env`, or sensitive files committed
- [x] Server starts with `mvn spring-boot:run` without errors
- [x] Architecture Diagram included in README
- [x] Original instructions replaced with own documentation
- [x] API endpoints documented with example requests/responses
- [x] Multiple meaningful commits in history
