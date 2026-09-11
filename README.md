# Raumlotse

Raumlotse is a web application for intelligent seminar and classroom reservation.

The system is designed to integrate with university infrastructure and suggest suitable rooms based on information such as course, number of participants, date, time, and additional requirements.

## Current Status

The project currently contains a Spring Boot backend, a Docker-based PostgreSQL setup, and pgAdmin for local database inspection.

A frontend is planned, but it is not part of the repository yet.

Available backend endpoint:

```text
GET /api/health
```

Expected response:

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
- Bean Validation
- Maven

### Database

- PostgreSQL 17
- pgAdmin 4

### Infrastructure

- Docker
- Docker Compose

### Planned

- React frontend
- TypeScript
- Vite
- Room search and reservation workflow

## Requirements

For running the project with Docker:

- Docker
- Docker Compose

For local backend development without Docker:

- Java 21
- Maven or the included Maven Wrapper

## Quick Start

Start the full local development setup:

```bash
docker compose up --build
```

This starts PostgreSQL, pgAdmin, and the Spring Boot backend.

## Local URLs

```text
Backend:      http://localhost:8080
Health check: http://localhost:8080/api/health
pgAdmin:      http://localhost:8081
PostgreSQL:   localhost:5432
```

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

Database access from the backend is prepared through Docker environment variables, but persistence logic is not implemented yet.

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

## Project Structure

```text
raumlotse/
+-- backend/                         # Spring Boot backend
|   +-- src/main/java/                # Application source code
|   +-- src/main/resources/           # Spring configuration
|   +-- src/test/java/                # Backend tests
|   +-- dockerfile                    # Backend Docker image
|   +-- pom.xml                       # Maven project configuration
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

## Goal

For the project presentation, the application should be publicly accessible so that students can try the room reservation workflow themselves.
