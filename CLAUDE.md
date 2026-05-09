# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a distributed healthcare microservices platform built with Spring Boot 3.4+ and Java 21. The system uses:
- **Spring Cloud Gateway** for API routing and JWT validation
- **gRPC** for synchronous inter-service communication
- **Kafka** for asynchronous event streaming
- **PostgreSQL** for persistent data storage
- **Protobuf** for data serialization

## Architecture & Services

### Core Services

**API Gateway** (port 4004)
- Entry point for all external requests
- Routes requests to downstream services (`/auth/**`, `/api/patients/**`)
- Validates JWT tokens via `JwtValidationGatewayFilterFactory` before allowing patient requests
- Exposes OpenAPI documentation endpoints

**Auth Service** (port 4005)
- JWT token generation and validation (`/login`, `/validate`)
- User management and authentication
- Uses Spring Security with bcrypt password hashing
- Database: PostgreSQL `auth-service-db`

**Patient Service** (port 4000)
- Core business logic for patient management
- Communicates with Billing Service via gRPC
- Produces `patient` Kafka events when patients are created/updated
- Uses Bean Validation (JSR 380) for input validation
- Database: PostgreSQL `patient-service-db`

**Billing Service** (port 9005)
- gRPC service for billing operations
- Compiled from `billing_service.proto`
- Called by Patient Service for billing-related transactions

**Analytics Service**
- Consumes `patient` Kafka events (protobuf-serialized)
- Processes event data asynchronously
- Demonstrates event-driven pattern

**Infrastructure**
- AWS CDK project for LocalStack deployment
- Defines cloud resources as code (Java-based CDK)

## Development Commands

### Build & Compile
```bash
# Build a specific service
cd patient-service && mvn clean package

# Run tests for a service
mvn test

# Compile proto files to Java (handled by Maven build)
mvn clean compile
```

### Running Services Locally
```bash
# Option 1: Maven Spring Boot plugin (recommended for development)
mvn spring-boot:run

# Option 2: Build and run JAR
mvn clean package
java -jar target/*.jar

# Set environment variables before running:
# Patient Service example:
$env:SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/db"
$env:SPRING_DATASOURCE_USERNAME="admin_user"
$env:SPRING_DATASOURCE_PASSWORD="password"
$env:SPRING_JPA_HIBERNATE_DDL_AUTO="update"
mvn spring-boot:run
```

### Building Docker Images
```bash
# Each service has a Dockerfile
docker build -t patient-service:latest -f patient-service/Dockerfile .
docker run -e SPRING_DATASOURCE_URL=jdbc:postgresql://host:5432/db patient-service:latest
```

### Integration Tests
```bash
cd integration-tests
mvn test
```

## Key Patterns & Technologies

### JWT Authentication Flow
1. Client calls `POST /auth/login` with credentials
2. Auth Service validates and returns JWT token in response
3. Client includes token in `Authorization: Bearer <token>` header
4. API Gateway intercepts requests to `/api/patients/**` and validates token via Auth Service
5. Auth Service endpoint `/validate` confirms token validity

### gRPC Service-to-Service Communication
- Patient Service calls Billing Service via gRPC on port 9005
- Proto definitions in `**/src/main/proto/*.proto` files
- Maven protobuf plugin (`xolstice.maven.plugins:protobuf-maven-plugin`) compiles `.proto` to Java
- Generated code includes service stubs and message classes (e.g., `BillingServiceGrpc.BillingServiceBlockingStub`)

### Kafka Event Streaming
- **Topic**: `patient` - PatientEvent messages (protobuf-serialized)
- **Producer**: Patient Service publishes events on patient creation/updates
- **Consumer**: Analytics Service consumes with group ID `analytics-service`
- Requires proper deserializer configuration: `ByteArrayDeserializer` for proto bytes

### Database Schema
- Each service owns its database (no shared databases)
- Hibernate `ddl-auto=update` auto-creates tables on startup
- Initial data via `data.sql` files (e.g., test users in auth-service)

### Proto File Compilation
- Build section in pom.xml includes `protobuf-maven-plugin`
- Compilation happens during `mvn clean compile` or full build
- Generated classes appear in `target/generated-sources/protobuf/`
- Also available at compile-time in IDEs after Maven sync

## Environment Variables

### Patient Service
```
SPRING_DATASOURCE_URL=jdbc:postgresql://patient-service-db:5432/db
SPRING_DATASOURCE_USERNAME=admin_user
SPRING_DATASOURCE_PASSWORD=password
BILLING_SERVICE_ADDRESS=billing-service
BILLING_SERVICE_GRPC_PORT=9005
SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092
SPRING_JPA_HIBERNATE_DDL_AUTO=update
SPRING_SQL_INIT_MODE=always
```

### Auth Service
```
SPRING_DATASOURCE_URL=jdbc:postgresql://auth-service-db:5432/db
SPRING_DATASOURCE_USERNAME=admin_user
SPRING_DATASOURCE_PASSWORD=password
SPRING_JPA_HIBERNATE_DDL_AUTO=update
SPRING_SQL_INIT_MODE=always
```

### API Gateway
```
auth.service.url=http://auth-service:4005
```

### Analytics Service
```
SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092
```

### Kafka Container (Docker)
```
KAFKA_CFG_ADVERTISED_LISTENERS=PLAINTEXT://kafka:9092,EXTERNAL://localhost:9094
KAFKA_CFG_CONTROLLER_LISTENER_NAMES=CONTROLLER
KAFKA_CFG_CONTROLLER_QUORUM_VOTERS=0@kafka:9093
KAFKA_CFG_LISTENER_SECURITY_PROTOCOL_MAP=CONTROLLER:PLAINTEXT,EXTERNAL:PLAINTEXT,PLAINTEXT:PLAINTEXT
KAFKA_CFG_LISTENERS=PLAINTEXT://:9092,CONTROLLER://:9093,EXTERNAL://:9094
KAFKA_CFG_NODE_ID=0
KAFKA_CFG_PROCESS_ROLES=controller,broker
```

## Debugging & Development

### Enable Remote Debugging
Set `JAVA_TOOL_OPTIONS` environment variable:
```
JAVA_TOOL_OPTIONS=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005
```

### API Testing
- HTTP request files in `api-requests/` for REST endpoints
- gRPC requests in `grpc-requests/` for service-to-service calls
- Use IDE's HTTP Client or tools like `grpcurl`

### Key Dependencies to Know
- `spring-cloud-gateway`: API Gateway routing
- `spring-security`: Authentication/Authorization
- `jjwt`: JWT token handling
- `grpc-spring-boot-starter`: gRPC service integration
- `spring-kafka`: Kafka producer/consumer
- `protobuf-java`: Protocol Buffer serialization
- `spring-boot-starter-data-jpa`: ORM with Hibernate

## Java/Spring Conventions

- **Java Version**: 21
- **Spring Boot**: 3.4.0+ (Spring Cloud 2024.0.0 for Gateway)
- **Package Structure**: `com.pm.<service-name>`
- **Validation**: Use JSR 380 annotations (`@NotNull`, `@Email`, etc.) on DTOs
- **Exception Handling**: Global `@ControllerAdvice` for REST services
- **Testing**: Spring Boot Test framework with embedded databases (H2 for auth-service)

## Common Tasks

### Adding a New Service
1. Create service directory with Maven structure
2. Add `pom.xml` with Spring Boot 3.4+ parent dependency
3. Create main application class with `@SpringBootApplication`
4. Define port in `application.properties`
5. Register route in API Gateway's `application.yml` if needed
6. Add database configuration and environment variables

### Extending Proto Definitions
1. Update `.proto` files in `src/main/proto/`
2. Run `mvn clean compile` to regenerate Java classes
3. Update service implementation to handle new message fields

### Adding Kafka Event Producer
1. Inject `KafkaTemplate` into service
2. Serialize message (protobuf) to bytes
3. Send via `kafkaTemplate.send(topic, bytes)`

### Publishing to Different Environments
- Build docker image from Dockerfile
- Push to registry
- Deploy with environment variables for target database/Kafka

## Engineering Rules

- Prefer minimal and safe changes
- Avoid unnecessary refactors
- Do not rewrite working configurations
- Preserve existing architecture patterns
- Do not add dependencies unless necessary
- Keep Docker networking consistent
- Avoid localhost references inside containers
- Maintain backward compatibility for APIs and proto contracts
- Explain root causes before applying fixes
- Only modify files related to the task
- Avoid placeholder implementations
- Ensure generated code compiles logically

## Debugging Workflow

When debugging:
1. Identify the exact failure point
2. Explain why the issue occurs
3. Propose the smallest reliable fix
4. Avoid speculative architectural changes
5. Verify Docker, ports, networking, environment variables, and service discovery first

## Code Style Preferences

- Use constructor injection only
- Keep controllers thin
- Put business logic in services
- Use DTOs for API boundaries
- Use global exception handling
- Prefer explicit and readable code
- Avoid deeply nested logic