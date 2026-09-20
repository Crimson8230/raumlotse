# Raumlotse

Raumlotse is a web application for intelligent seminar and classroom reservation.

The system is designed to integrate with university infrastructure and suggest suitable rooms based on information such as course, number of participants, date, time, and additional requirements.

## Current Status

The project contains a Spring Boot backend with a real persistence layer, a React frontend, a Docker-based PostgreSQL setup, and pgAdmin for local database inspection.

Email/password sign-in is available at `/login`. All application API routes require an authenticated session except `GET /api/health` and the CSRF/login bootstrap endpoints. Sessions expire after 30 minutes of inactivity; the browser stores the session in an HttpOnly, same-site cookie. Production must run over HTTPS and set `SESSION_COOKIE_SECURE=true`.

For local Compose use, copy `.env.example` to `.env`, replace every placeholder, and keep `.env` untracked. `AUTH_ATTEMPT_HMAC_KEY` must be a stable base64-encoded secret containing at least 32 random bytes; do not regenerate it on application restart. Local fixture account creation is opt-in through the `local-auth-fixture` Spring profile and requires `AUTH_FIXTURE_EMAIL`, `AUTH_FIXTURE_DISPLAY_NAME`, and `AUTH_FIXTURE_PASSWORD`. It never resets an existing account password. Never enable that profile against production data.

The first feature, **room management**, is implemented: administrators can maintain a catalog of buildings and their floors, an equipment catalog (projector, whiteboard, ...), and rooms (name, floor, seating arrangements with capacities, assigned equipment). Rooms, buildings, floors, and equipment types can each be created, renamed, deactivated/reactivated, and deleted. See [`specs/001-room-management/`](specs/001-room-management/) for the full specification, data model, and API contract.

Available backend endpoints:

```text
GET  /api/health

GET    /api/rooms
POST   /api/rooms
GET    /api/rooms/{roomId}
PUT    /api/rooms/{roomId}
DELETE /api/rooms/{roomId}
POST   /api/rooms/{roomId}/deactivate
POST   /api/rooms/{roomId}/reactivate

GET    /api/buildings
POST   /api/buildings
PUT    /api/buildings/{buildingId}
DELETE /api/buildings/{buildingId}
POST   /api/buildings/{buildingId}/deactivate
POST   /api/buildings/{buildingId}/reactivate
GET    /api/buildings/{buildingId}/floors
POST   /api/buildings/{buildingId}/floors

PUT    /api/floors/{floorId}
DELETE /api/floors/{floorId}
POST   /api/floors/{floorId}/deactivate
POST   /api/floors/{floorId}/reactivate

GET    /api/equipment-types
POST   /api/equipment-types
PUT    /api/equipment-types/{equipmentTypeId}
DELETE /api/equipment-types/{equipmentTypeId}
POST   /api/equipment-types/{equipmentTypeId}/deactivate
POST   /api/equipment-types/{equipmentTypeId}/reactivate
```

The full, versioned contract (request/response shapes, status codes) lives in [`specs/001-room-management/contracts/openapi.yaml`](specs/001-room-management/contracts/openapi.yaml).

`GET /api/health` still returns:

```json
{
  "status": "ok"
}
```

## Tech Stack

### Backend

- Java 21
- Spring Boot
- Spring Web MVC
- Spring Data JPA (Hibernate)
- Flyway (SQL migrations under `backend/src/main/resources/db/migration/`)
- Bean Validation
- Maven
- Testcontainers (PostgreSQL) for integration tests

### Frontend

- React
- TypeScript
- Vite
- React Router
- lucide-react
- Vitest + React Testing Library
- ESLint

### Database

- PostgreSQL 17
- pgAdmin 4

### Infrastructure

- Docker
- Docker Compose

### Planned

- Authentication/authorization (room management is currently unauthenticated by design — see `specs/001-room-management/spec.md`)
- Room search and reservation workflow

## Requirements

For running the project with Docker:

- Docker
- Docker Compose

For local backend development without Docker:

- Java 21
- Maven or the included Maven Wrapper

For local frontend development:

- Node.js
- npm

## Quick Start

Start the backend services:

```bash
docker compose up --build
```

This starts PostgreSQL, pgAdmin, and the Spring Boot backend.

Start the frontend in a second terminal:

```bash
cd frontend
npm install
npm run dev
```

## Local URLs

```text
Frontend:     http://localhost:5173
Backend:      http://localhost:8080
Health check: http://localhost:8080/api/health
pgAdmin:      http://localhost:8081
PostgreSQL:   localhost:5432
```

During local frontend development, Vite proxies requests from `/api` to `http://localhost:8080`.

## pgAdmin Login

```text
Email:    admin@admin.com
Password: admin
```

The PostgreSQL server is preconfigured in pgAdmin through `docker-config/pgadmin_servers.json`.

## Database Credentials

```text
Database: raumlotse
User:     raumlotse
Password: raumlotse
Host:     localhost
Port:     5432
```

Inside Docker, the backend connects to PostgreSQL through the Docker service name:

```text
jdbc:postgresql://db:5432/raumlotse
```

On startup, the backend applies its Flyway migrations automatically against this database (creating the `building`, `floor`, `room`, `seating_arrangement`, and `equipment_type` tables, and seeding the equipment catalog with `Projector` and `Whiteboard`).

> **Note**: Spring Boot 4.1.1 does **not** auto-configure Flyway (unlike earlier Spring Boot versions — there is no `FlywayAutoConfiguration` on the classpath). Migrations are triggered manually by a `Flyway` bean in `backend/src/main/java/at/mci/igp/raumlotse/config/FlywayConfig.java`. If you ever see a backend error like `relation "..." does not exist`, check that this bean actually ran (look for Flyway log lines on startup) before assuming a connection/port problem — see `specs/001-room-management/research.md` §1 for the full story.

## Backend Development

Run the backend locally from the `backend` directory.

On macOS/Linux:

```bash
cd backend
./mvnw spring-boot:run
```

On Windows:

```bash
cd backend
mvnw.cmd spring-boot:run
```

Run backend tests:

```bash
cd backend
./mvnw test
```

On Windows:

```bash
cd backend
mvnw.cmd test
```

Some tests (names ending in `IntegrationTest`, plus `RaumlotseApplicationTests`) start a real PostgreSQL via [Testcontainers](https://testcontainers.com/) and therefore require Docker to be running. To run only the tests that don't need Docker:

```bash
./mvnw "-Dtest=!*IntegrationTest,!RaumlotseApplicationTests" test
```

## Frontend Development

Run the frontend locally from the `frontend` directory.

```bash
cd frontend
npm install
npm run dev
```

Build the frontend:

```bash
cd frontend
npm run build
```

Run frontend tests:

```bash
cd frontend
npm run test
```

Run frontend linting:

```bash
cd frontend
npm run lint
```

## Project Structure

```text
raumlotse/
+-- backend/                         # Spring Boot backend
|   +-- src/main/java/.../raumlotse/
|   |   +-- config/                   # Manual bean wiring (e.g. FlywayConfig)
|   |   +-- controller/               # REST controllers
|   |   +-- service/                  # Business logic
|   |   +-- repository/               # Spring Data JPA repositories
|   |   +-- domain/                   # JPA entities
|   |   +-- dto/                      # Request/response DTOs
|   |   +-- exception/                # Domain exceptions + GlobalExceptionHandler
|   +-- src/main/resources/
|   |   +-- db/migration/             # Flyway SQL migrations
|   |   +-- application.yaml          # Spring configuration
|   +-- src/test/java/                # Backend tests (unit, MockMvc, Testcontainers)
|   +-- dockerfile                    # Backend Docker image
|   +-- pom.xml                       # Maven project configuration
+-- frontend/                        # React frontend
|   +-- src/
|   |   +-- API/                      # Frontend API clients
|   |   +-- components/               # Reusable UI components (forms, catalogs)
|   |   +-- pages/                    # Route-level pages
|   |   +-- types/                    # Shared TypeScript types
|   |   +-- App.tsx                   # Frontend route setup
|   |   +-- main.tsx                  # React entrypoint
|   +-- package.json                  # Frontend dependencies and scripts
|   +-- vite.config.ts                # Vite + Vitest configuration
+-- specs/001-room-management/       # Feature spec, plan, tasks, data model, API contract
+-- docker-config/
|   +-- pgadmin_servers.json          # pgAdmin server preset
+-- docker-compose.yml                # Local development services
+-- README.md
```

## Docker Services

The Docker Compose setup contains three services:

```text
db       PostgreSQL database
pgadmin  Browser-based database administration
backend  Spring Boot REST API
```

The database container includes a health check, and both pgAdmin and the backend wait until PostgreSQL is ready.

The frontend is currently started separately with Vite during local development.

## Goal

For the project presentation, the application should be publicly accessible so that students can try the room reservation workflow themselves.
