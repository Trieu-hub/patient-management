1. What was wrong
   api-gateway container crashed at startup — BeanCreationException on jwtValidationGatewayFilterFactory.

2. Root cause
   JwtValidationGatewayFilterFactory.java:18 injects @Value("${auth.service.url}"). That property was never defined anywhere — not in application.yml, application-prod.yml, or any
   env var.

3. Changes made
- Added auth.service.url: http://auth-service:4005 to both YML files
- Replaced all host.docker.internal in application-prod.yml with container names
- Fixed typo: http:/host.docker.internal → http://auth-service:4005

4. Files modified
- api-gateway/src/main/resources/application.yml
- api-gateway/src/main/resources/application-prod.yml

5. Architecture assumptions
- All services in Docker Compose on a shared network
- Container names: auth-service:4005, patient-service:4000, api-gateway:4004
- patient-service has no exposed host port — internal only

6. Remaining issues
- No docker-compose.yml found in repo — must exist or be created with matching service names
- application-prod.yml profile activation is unset — unclear when it activates

7. Next steps
7.1. Confirm/create docker-compose.yml with all services on a shared network
7.2. Rebuild: docker build -t api-gateway:latest -f api-gateway/Dockerfile .
7.3. Check logs: docker logs <api-gateway-container>
7.4. Verify DNS: docker exec <api-gateway> curl http://auth-service:4005/actuator/health
7.5. Test full JWT flow: login → get token → call /api/patients with Bearer token


Auth Service JWT Secret — Session Handoff

1. What was wrong
   auth-service container crashed at startup: Could not resolve placeholder 'jwt.secret'. Bean chain AuthController → AuthService → JwtUtil failed at JwtUtil constructor.

2. Root cause
   JwtUtil.java:20 uses @Value("${jwt.secret}"). Spring Boot resolves this from env var JWT_SECRET via relaxed binding. JWT_SECRET existed in the local dev shell but was never
   passed into the Docker container — host env vars are not inherited by containers.

3. Changes made
- Added jwt.secret=${JWT_SECRET} to application.properties (makes dependency explicit)
- Created docker-compose.yml at repo root with all 8 services on a shared bridge network, injecting JWT_SECRET into auth-service with a dev-only base64 secret (32 bytes, valid
  for HS256)

4. Files modified
- auth-service/src/main/resources/application.properties
- D:\patient-management\docker-compose.yml ← created from scratch

5. Architecture assumptions
- All containers on patient-management-network (bridge); container DNS resolves by service name
- Two separate Postgres containers: auth-service-db, patient-service-db (both internal port 5432)
- Kafka: Bitnami KRaft mode, internal kafka:9092, external localhost:9094
- api-gateway uses SPRING_PROFILES_ACTIVE: prod to activate application-prod.yml

6. Remaining issues
- Dev JWT secret is hardcoded in docker-compose — replace with openssl rand -base64 32 before any non-local use
- No DB healthchecks — services may fail on cold start if Postgres isn't ready yet
- api-gateway prod profile route correctness unverified end-to-end

7. Next debugging steps
   docker compose up --build          # from D:\patient-management
   docker logs auth-service           # should see "Started AuthServiceApplication"
   POST localhost:4005/login          # verify token issued
   POST localhost:4005/validate       # verify token accepted
   GET  localhost:4004/api/patients   # full gateway JWT flow
   If cold-start DB errors appear, add restart: on-failure or depends_on: condition: service_healthy with a Postgres healthcheck.
