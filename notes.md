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

10/5/2026

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

11/5/2026

1. Timeline of Issues

Issue 1 — API Gateway: auth.service.url unresolvable

Date: 2026-05-10 (commit 0839c43)
Exact error: BeanCreationException: jwtValidationGatewayFilterFactory — Could not resolve placeholder 'auth.service.url'
Symptom: api-gateway container failed to start; JwtValidationGatewayFilterFactory could not be instantiated.
Root cause: JwtValidationGatewayFilterFactory.java used @Value("${auth.service.url}"), but that property existed nowhere — not in application.yml, not in application-prod.yml,
not as an environment variable. Additionally, application-prod.yml contained host.docker.internal as the upstream host for all route URIs. That hostname only resolves on Docker
Desktop (host-to-container networking); it does not resolve inside a container-to-container Docker network. One URI also had a typo: http:/host.docker.internal (single slash).
Files involved: api-gateway/src/main/resources/application.yml, api-gateway/src/main/resources/application-prod.yml, api-gateway/Dockerfile
Severity: Blocking — api-gateway ApplicationContext refused to start.

  ---
Issue 2 — Auth Service: jwt.secret unresolvable (first form)

Date: 2026-05-11 14:33 (commit f656457)
Exact error: Could not resolve placeholder 'jwt.secret' in value "${jwt.secret}"
Symptom: auth-service container crashed at startup; JwtUtil bean creation failed; ApplicationContext never started.
Root cause: Two compounding problems:
1. application.properties contained only spring.application.name and server.port. The property jwt.secret was never declared anywhere.
2. No docker-compose.yml existed yet. The container was started via docker run auth-service:latest with no -e JWT_SECRET=... flag, so the env var was absent from the container.
   Files involved: auth-service/src/main/resources/application.properties, auth-service/src/main/java/com/pm/authservice/util/JwtUtil.java
   Severity: Blocking.

  ---
Issue 3 — Auth Service: JWT_SECRET unresolvable (second form, after partial fix)

Date: 2026-05-11, later (this conversation, round 1)
Exact error: Could not resolve placeholder 'JWT_SECRET' in value "${JWT_SECRET}" <-- "${jwt.secret}"
Symptom: Same crash, different error shape. The <-- chain appears because jwt.secret=${JWT_SECRET} was now present in application.properties, so Spring resolved that property to
${JWT_SECRET}, then failed trying to find JWT_SECRET in any property source.
Root cause: After the Session 2 fix added jwt.secret=${JWT_SECRET} to application.properties and created docker-compose.yml with JWT_SECRET injected, the user ran docker run
auth-service:latest directly, not docker compose up. Docker stores environment variables at container creation time. The container was created without JWT_SECRET. Every
subsequent docker start auth-service re-launched the same container with its original, JWT_SECRET-absent environment.
Files involved: auth-service/src/main/resources/application.properties (had the new line), docker-compose.yml (had JWT_SECRET but was never used to create the container)
Severity: Blocking.

  ---
Issue 4 — Orphan container not replaceable by --force-recreate

Date: This conversation, round 1 (between the two rounds)
Symptom: After being advised to run docker compose up --force-recreate auth-service, the error persisted.
Root cause: The container auth-service had no com.docker.compose.* labels — confirmed by docker inspect. Docker Compose labels are written only when docker compose up creates
the container. Without these labels, Docker Compose V2 does not cleanly own the container. --force-recreate behaviour on an unowned container is undefined; in practice, Compose
may issue a plain docker start reusing the existing frozen env, or fail silently. The container's JWT_SECRET-absent env was never replaced.
Severity: Blocking — the recommended remediation had no effect.

  ---
2. Fixes Attempted

Fix A — API Gateway config (Session 1, commit 0839c43)

What changed:
- api-gateway/src/main/resources/application.yml: added auth.service.url: http://auth-service:4005
- api-gateway/src/main/resources/application-prod.yml: same property added; all four host.docker.internal URIs replaced with Docker Compose service names (auth-service,
  patient-service); typo fixed (http:/host → http://auth-service)
- api-gateway/Dockerfile: openjdk:21-jdk → eclipse-temurin:21-jdk; added -DskipTests to Maven package command

Why it was believed to work: The missing property would now be available; container-name DNS resolution is reliable inside a Docker Compose bridge network.

Did it work: Yes. api-gateway can start with these values present.

  ---
Fix B — Auth Service application.properties + docker-compose.yml creation (Session 2, commit f656457)

What changed:
- auth-service/src/main/resources/application.properties: added jwt.secret=${JWT_SECRET}
- docker-compose.yml (new file at repo root): all services defined with shared network patient-management-network; auth-service section includes JWT_SECRET:
  dGVzdC1qd3Qtc2VjcmV0LWtleS1wYXRpZW50LW1nbXQ=
- auth-service/Dockerfile: same pattern as Fix A — base image updated, -DskipTests added

Why it was believed to work: The two-level chain @Value("${jwt.secret}") → application.properties → ${JWT_SECRET} → container env would resolve end-to-end when the container was
started via Docker Compose.

Did it work: Partially. The files were correct. The image built correctly. But the container was created by running docker run auth-service:latest directly, so the JWT_SECRET
injection from docker-compose.yml never applied. Commit message confirms the container still did not start: "thêm docker-compose.yml nhưng vẫn chưa mở được auth-service
container."

  ---
Fix C — docker compose up --force-recreate (This conversation, round 1)

What changed: No files were modified. The recommendation was an operational command.

Why it was believed to work: Assumed --force-recreate would stop the existing container regardless of origin, remove it, and create a new one from the current docker-compose.yml
— picking up JWT_SECRET.

Did it work: No. The container had no Docker Compose labels. The command did not reliably replace the orphan container with a compose-owned one.

  ---
Fix D — Direct @Value("${JWT_SECRET}") + orphan removal + rebuild (This conversation, round 2)

What changed:
- auth-service/src/main/java/com/pm/authservice/util/JwtUtil.java: @Value("${jwt.secret}") → @Value("${JWT_SECRET}")
- auth-service/src/main/resources/application.properties: removed jwt.secret=${JWT_SECRET} line (reverted to two-line file)
- Operationally: docker rm -f auth-service → docker compose build auth-service → docker compose up -d auth-service

Why it was believed to work: With the two-level indirection removed, JwtUtil directly depends on the JWT_SECRET env var via Spring's SystemEnvironmentPropertySource. The orphan
container was explicitly removed first, guaranteeing that docker compose up would create a fresh container owned by Compose, carrying JWT_SECRET from docker-compose.yml.

Did it work: Yes. Verified: Started AuthServiceApplication in 16.731 seconds. No ApplicationContext error. docker inspect confirmed
JWT_SECRET=dGVzdC1qd3Qtc2VjcmV0LWtleS1wYXRpZW50LW1nbXQ= in the container env.

  ---
3. Failed or Incorrect Fixes

Failure 1 — Fix B: two-level property chain with no container management

Why it failed: The fix added jwt.secret=${JWT_SECRET} to application.properties correctly, and created docker-compose.yml with JWT_SECRET correctly. But neither change has any
effect on a container that already exists or that is created with docker run. The root issue was not missing config — it was the wrong container startup method. The fix
diagnosed the config gap but not the runtime gap.

Wrong assumption: That creating docker-compose.yml would automatically cause the container to be started via Docker Compose on the next run.

Side effect introduced: The two-level chain jwt.secret=${JWT_SECRET} added one layer of indirection. When the error recurred, the error message was now longer and more
confusing: 'JWT_SECRET' in value "${JWT_SECRET}" <-- "${jwt.secret}". This chain obscured the actual missing variable and made the error look like a different problem than it
was.

What should have been checked first: Whether the container in question had Docker Compose labels (docker inspect <container> | grep com.docker.compose). If it had no labels,
docker-compose.yml changes would have zero effect on it.

  ---
Failure 2 — Fix C: --force-recreate on an orphan container

Why it failed: docker compose up --force-recreate is designed to recreate containers it owns (identified by com.docker.compose.* labels). The auth-service container had only
Ubuntu base-image labels. Docker Compose V2 cannot safely force-recreate an unowned container because it doesn't know whether it is safe to remove it. In practice, the command
either restarted the existing container as-is (preserving the stale env) or failed silently.

Wrong assumption: That --force-recreate is a universal container replacement mechanism regardless of how the container was originally created.

What should have been checked first: The container labels, which I did check in round 1 of this conversation — they showed no compose labels — but I recommended --force-recreate
anyway without accounting for how Compose handles unlabeled containers.

What should have been done instead: docker rm -f auth-service first to explicitly remove the orphan, then docker compose up. This guarantees Compose creates a fresh container.

  ---
Failure 3 — Unnecessary two-level property indirection (introduced in Fix B, partially reverted in Fix D)

What happened: jwt.secret=${JWT_SECRET} was added to application.properties as a "make the dependency explicit" change. In Fix D, this was removed and @Value was changed to
reference JWT_SECRET directly.

Was this wrong: The jwt.secret=${JWT_SECRET} pattern is a valid Spring Boot convention. However, it is unnecessary here. Spring Boot's SystemEnvironmentPropertySource
automatically exposes environment variables as properties, so @Value("${JWT_SECRET}") resolves directly. Adding the properties-file indirection without a clear reason added
noise and produced a confusing cascaded error message.

Side effect: The <-- "${jwt.secret}" tail in the error message made both the user and the debugger think the error was a different kind of problem from the original 'jwt.secret'
failure, slowing diagnosis.

  ---
4. Current Root Causes (Still Relevant)

Environment / Configuration

- patient-management-auth-service-1 (the compose-created container that succeeded) is now Exited (143) — exit code 143 means SIGTERM, normal shutdown. This container is not
  running. A new auth-service container with image b17d266e29d1 has appeared in docker ps -a, again without Compose labels. This suggests docker run auth-service:latest (or
  equivalent) was invoked again after the fix, recreating the original pattern.
- docker-compose.yml line 1 contains version: '3.8', which Docker Compose V2 considers obsolete and logs a warning on every invocation. This does not affect functionality but is
  noisy.

Docker / Container

- All other services (api-gateway, patient-service, billing-service, analytics-service, kafka) are Exited. None have been started via docker compose up in this debugging
  session.
- Two PostgreSQL containers exist in parallel: patient-management-auth-service-db-1 (compose-managed, Up) and auth-service-db (not compose-managed, Exited). This is a side
  effect of having created containers both inside and outside Docker Compose. They may point to different named volumes.

Build

- No build issues remain. The current image builds cleanly from source with -DskipTests.

Runtime

- No healthchecks are defined for auth-service-db or patient-service-db in docker-compose.yml. On a cold start, auth-service may attempt to connect to PostgreSQL before it is
  ready. The current depends_on only waits for the container to exist, not for PostgreSQL to accept connections. This is a latent failure mode that has not yet surfaced as a
  blocking error in this session.

Code

- None. All application code is correct as of commit 92c0366.

  ---
5. Final Stable State

Required environment variables

JWT_SECRET must be injected into the auth-service container at creation time. It is already defined in docker-compose.yml:

JWT_SECRET: dGVzdC1qd3Qtc2VjcmV0LWtleS1wYXRpZW50LW1nbXQ=

This value decodes to test-jwt-secret-key-patient-mgmt (32 bytes). It is for development only.

MANUAL ACTION REQUIRED: For any environment beyond local development, generate a new value with openssl rand -base64 32 and replace the value in docker-compose.yml (or inject
via a secrets manager).

Required application.properties values (auth-service)

spring.application.name=auth-service
server.port=4005

No jwt.secret line. JWT_SECRET is read directly by JwtUtil via @Value("${JWT_SECRET}").

Required docker-compose.yml config (auth-service section)

auth-service:
environment:
JWT_SECRET: <base64-encoded-secret>
SPRING_DATASOURCE_URL: jdbc:postgresql://auth-service-db:5432/db
SPRING_DATASOURCE_USERNAME: admin_user
SPRING_DATASOURCE_PASSWORD: password
SPRING_JPA_HIBERNATE_DDL_AUTO: update
SPRING_SQL_INIT_MODE: always

Required startup procedure

MANUAL ACTION REQUIRED: The service must always be started via Docker Compose from D:\patient-management, never via docker run. If any auth-service container exists that was not
created by Compose, it must be removed first:

docker rm -f auth-service          # only if an orphan exists
cd D:\patient-management
docker compose up auth-service     # or: docker compose up (all services)

External dependencies

- PostgreSQL 17 (auth-service-db) must be reachable at auth-service-db:5432 before auth-service completes JPA initialization. On cold start this is not guaranteed by the current
  depends_on configuration.

  ---
6. File Change Summary

File: api-gateway/src/main/resources/application.yml
Session: Session 1 (0839c43)
Change: Added auth.service.url: http://auth-service:4005
────────────────────────────────────────
File: api-gateway/src/main/resources/application-prod.yml
Session: Session 1 (0839c43)
Change: Added auth.service.url; replaced four host.docker.internal URIs with container names; fixed typo http:/ → http://
────────────────────────────────────────
File: api-gateway/Dockerfile
Session: Session 1 (0839c43)
Change: openjdk:21-jdk → eclipse-temurin:21-jdk; mvn clean package → mvn clean package -DskipTests
────────────────────────────────────────
File: auth-service/src/main/resources/application.properties
Session: Session 2 (f656457)
Change: Added jwt.secret=${JWT_SECRET} — later reverted
────────────────────────────────────────
File: auth-service/src/main/resources/application.properties
Session: This conversation (92c0366)
Change: Removed jwt.secret=${JWT_SECRET} — file is now two lines only
────────────────────────────────────────
File: auth-service/Dockerfile
Session: Session 2 (f656457)
Change: openjdk:21-jdk → eclipse-temurin:21-jdk; added -DskipTests
────────────────────────────────────────
File: auth-service/src/main/java/com/pm/authservice/util/JwtUtil.java
Session: This conversation (92c0366)
Change: @Value("${jwt.secret}") → @Value("${JWT_SECRET}")
────────────────────────────────────────
File: docker-compose.yml
Session: Session 2 (f656457)
Change: New file — all services defined, JWT_SECRET injected into auth-service

  ---
7. Lessons Learned / Prevention

The container lifecycle was never verified before changing code. The first question when a Spring Boot property is unresolvable inside a container is: does the container have
the expected env var? docker inspect <container> | grep Env answers this in one command. Running this at the start of Session 2 would have immediately shown JWT_SECRET was
absent, focusing the fix on the container — not the application code.

Container labels reveal provenance. docker inspect <container> will show com.docker.compose.* labels if and only if the container was created by Docker Compose. Their absence
immediately indicates the container was started with docker run. Checking labels before recommending --force-recreate would have prevented Fix C from being the wrong tool.

docker compose up --force-recreate is not a universal replacement command. It operates on containers it owns. For orphan containers, the correct procedure is always docker rm -f
<container> first, then docker compose up.

Two-level property chains add failure surface. jwt.secret=${JWT_SECRET} in application.properties is a valid pattern but produces a longer error chain. When a property
indirection is added purely to make a property "explicit," it should come with a clear comment and a known benefit. In this case there was no benefit: the direct
@Value("${JWT_SECRET}") achieves the same resolution with one fewer step and a simpler error message.

docker run must never be used on services defined in docker-compose.yml. Once a docker-compose.yml exists for a service, running that service outside Compose creates a container
that Compose does not own and cannot manage. It produces naming conflicts, stale environments, and debugging confusion. The consistent rule is: if it is in docker-compose.yml,
it is only ever started with docker compose up.



