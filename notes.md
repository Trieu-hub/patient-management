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
