# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A microservices-based patient management system built with Java, Spring Boot 3.4.x, and Java 21. The system uses an API Gateway for routing, with services communicating via gRPC and Kafka. The architecture is designed for distributed, scalable healthcare management.

## Architecture

### Services

1. **api-gateway** (port 4004) — Spring Cloud Gateway routing layer
   - Routes requests to appropriate microservices
   - Applies JwtValidation filter for protected endpoints
   - Exposes OpenAPI documentation via `/api-docs/*` routes

2. **auth-service** (port 4005) — User authentication and JWT token management
   - Spring Security with JWT (jjwt library, version 0.12.6)
   - User database with email/password/role
   - Integration with H2 database for testing

3. **patient-service** (port 4000) — Core patient data management
   - REST API for patient CRUD operations
   - JPA/Hibernate with PostgreSQL
   - gRPC client communication with billing-service
   - Kafka producer for event publishing
   - Validation groups (CreatePatientValidationGroup) for request DTO validation

4. **billing-service** — Billing operations
   - gRPC service implementation
   - Communicates with patient-service via gRPC
   - Port 9005 for gRPC endpoint

5. **analytics-service** — Analytics and reporting
   - Kafka consumer for event processing

6. **infrastructure** — Shared infrastructure code

7. **integration-tests** — End-to-end tests using RestAssured
   - Base URI: http://localhost:4004 (API Gateway)
   - Tests authenticate via `/auth/login` then call protected routes

## Build & Test

All services use Maven. Each service has its own `pom.xml`.

### Build a single service
```bash
cd patient-service
mvn clean package
```

### Run tests for a service
```bash
cd patient-service
mvn test
```

### Run integration tests
```bash
cd integration-tests
mvn test
```

The integration tests assume services are running and accessible through the API Gateway on http://localhost:4004.

## Key Technologies

- **Spring Boot**: 3.4.0/3.4.1 (Spring Cloud Gateway 2024.0.0)
- **Java**: 21
- **ORM**: Spring Data JPA / Hibernate
- **Database**: PostgreSQL (production), H2 (testing)
- **RPC**: gRPC (protobuf 4.29.1, grpc-spring-boot-starter 3.1.0)
- **Messaging**: Spring Kafka (version 3.3.0)
- **Authentication**: Spring Security + jjwt (0.12.6)
- **API Documentation**: springdoc-openapi (2.6.0 - 2.7.0)
- **Testing**: JUnit 5, RestAssured

## Service Communication

- **gRPC**: Patient Service → Billing Service (for billing operations)
  - BillingServiceGrpcClient in patient-service calls billing-service:9005
  
- **Kafka**: Patient Service → Other services
  - KafkaProducer publishes events for consumption by analytics/notification services
  - Bootstrap server: kafka:9092 (Docker)

## Database Setup

### Patient Service
```
URL: jdbc:postgresql://patient-service-db:5432/db
User: admin_user
Password: password
Hibernate DDL: update
```

### Auth Service
```
URL: jdbc:postgresql://auth-service-db:5432/db
User: admin_user
Password: password
Hibernate DDL: update
```

Default test user (Auth Service):
- Email: `testuser@test.com`
- Password: `password123` (stored as bcrypt hash)
- Role: `ADMIN`

## Important Patterns

### PatientRequestDTO Validation
Uses Spring validation groups for request validation. `CreatePatientValidationGroup` is applied to POST operations. Check `PatientRequestDTO` for field-level constraints.

### Exception Handling
Global exception handler in `GlobalExceptionHandler` converts service exceptions to HTTP responses. Custom exceptions: `PatientNotFoundException`, `EmailAlreadyExistsException`.

### API Documentation
OpenAPI/Swagger UI available at service `/swagger-ui.html`. Gateway routes documentation at `/api-docs/patients` and `/api-docs/auth`.

## Common Commands

### Build specific service
```bash
mvn -f patient-service/pom.xml clean package
```

### Build all services (requires running from root with proper setup)
```bash
mvn clean package
```

### Run a service locally (with proper environment variables set)
```bash
cd patient-service
mvn spring-boot:run
```

### View service logs
Services configured for debug mode with Java Debug Wire Protocol (JDWP) on port 5005 (see `JAVA_TOOL_OPTIONS` in README).
