# ConnectHub Docker Setup Documentation

## Quick Start

### Prerequisites
- Docker & Docker Compose installed
- All services built with `mvn clean package -DskipTests`

### Step 1: Set Environment Variables
```bash
cp .env.example .env
# Edit .env with your actual values
```

### Step 2: Build & Start All Services
```bash
docker-compose up --build
```

### Step 3: Wait for Services to be Healthy
```bash
docker-compose ps
# All services should show "healthy" status
```

### Step 4: Access Services

| Service | URL | Credentials |
|---------|-----|-------------|
| API Gateway | http://localhost:8080 | No auth |
| Eureka Dashboard | http://localhost:8761 | eureka:eureka |
| Admin Dashboard | http://localhost:8089 | admin:admin_password |
| SonarQube | http://localhost:9000 | admin:admin |
| Swagger UI | http://localhost:8080/swagger-ui.html | via gateway |

### Service Ports
```
8080 - API Gateway
8081 - Auth Service
8082 - Room Service
8083 - Message Service
8084 - Notification Service
8085 - Payment Service
8086 - Presence Service
8087 - WebSocket Service
8088 - Media Service
8089 - Admin Server
8761 - Service Registry (Eureka)
9000 - SonarQube
3306 - MySQL
6379 - Redis
9092 - Kafka
2181 - Zookeeper
```

## Useful Commands

### View Logs
```bash
# All services
docker-compose logs -f

# Specific service
docker-compose logs -f auth-service
```

### Stop Services
```bash
docker-compose down
```

### Remove Everything (including volumes)
```bash
docker-compose down -v
```

### Rebuild Single Service
```bash
docker-compose up --build auth-service
```

### Check Service Status
```bash
docker-compose ps
```

### Enter Service Shell
```bash
docker-compose exec auth-service sh
```

## Docker Networking

All services communicate via the `connecthub-network` bridge network:

- Services reference each other by container name
- Example: `redis:6379`, `mysql:3306`, `kafka:9092`
- No localhost references in Docker environment

## Volumes

- `mysql_data` - MySQL persistent data
- `redis_data` - Redis persistence
- `kafka_data` - Kafka logs
- `sonarqube_data` - SonarQube data

## Environment Variable Override

To override environment variables at runtime:
```bash
docker-compose run -e MYSQL_PASSWORD=newpassword auth-service
```

## Troubleshooting

### Service fails to connect to MySQL
- Wait for MySQL to be healthy: `docker-compose logs mysql`
- Verify MYSQL_PASSWORD in .env matches docker-compose.yml

### Services can't find Eureka
- Ensure service-registry is healthy first
- Check network connectivity: `docker network inspect connecthub-network`

### Kafka broker unhealthy
- Check Zookeeper is running first
- Verify KAFKA_ADVERTISED_LISTENERS uses kafka:9092 (not localhost)

### Memory issues
- Increase Docker Desktop memory allocation
- Or reduce number of running services

## Production Deployment

For production deployment:
1. Use managed services (AWS RDS for MySQL, AWS ElastiCache for Redis)
2. Replace hardcoded credentials with AWS Secrets Manager
3. Use CloudFormation or Terraform for infrastructure
4. Deploy on ECS, EKS, or Kubernetes
5. Enable logging aggregation (CloudWatch, ELK)

## Notes

- All services use Alpine Linux for minimal image size
- Healthchecks enabled for resilience
- Restart policy set to `unless-stopped`
- No persistent volumes for application code (stateless)
