# ConnectHub Microservices - Docker Quick Reference

## ⚡ Quick Commands

```bash
# Start all services (first time - takes 5-10 minutes)
docker-compose up --build

# Start without rebuild
docker-compose up -d

# Stop all services
docker-compose down

# View running services
docker-compose ps

# View logs (all services)
docker-compose logs -f

# View logs (specific service)
docker-compose logs -f auth-service

# View service status
docker-compose ps service-registry

# Restart a service
docker-compose restart auth-service

# Rebuild specific service
docker-compose up --build auth-service

# Remove all containers and volumes
docker-compose down -v

# Enter service shell
docker-compose exec auth-service sh

# Run command in service
docker-compose exec mysql mysql -uroot -p$MYSQL_PASSWORD -e "SHOW DATABASES;"
```

---

## 📊 Service Health Status

After startup, check health with:

```bash
# API Gateway
curl http://localhost:8080/actuator/health

# Auth Service
curl http://localhost:8081/actuator/health

# Service Registry
curl http://localhost:8761/eureka/apps

# Admin Dashboard
curl http://localhost:8089/actuator/health

# All at once
for port in 8080 8081 8082 8083 8084 8085 8086 8087 8088 8089 8761; do
  echo "Port $port: $(curl -s http://localhost:$port/actuator/health | jq .status)"
done
```

---

## 🌐 Service Access URLs

| Service | URL | Auth | Purpose |
|---------|-----|------|---------|
| API Gateway | http://localhost:8080 | None | Main entry point |
| Swagger UI | http://localhost:8080/swagger-ui.html | None | API Documentation |
| Eureka Dashboard | http://localhost:8761 | eureka:eureka | Service discovery |
| Admin Dashboard | http://localhost:8089 | admin:admin_password | Monitoring |
| SonarQube | http://localhost:9000 | admin:admin | Code quality |
| MySQL | localhost:3306 | root:password | Database |
| Redis | localhost:6379 | None | Cache |
| Kafka | localhost:9092 | None | Message broker |

---

## 📋 Database Operations

### MySQL Access
```bash
# Connect to MySQL
docker-compose exec mysql mysql -uroot -p$MYSQL_PASSWORD

# Inside MySQL:
SHOW DATABASES;
USE connecthub_auth;
SHOW TABLES;
SELECT COUNT(*) FROM users;
```

### View Schema
```bash
docker-compose exec mysql mysqldump -uroot -p$MYSQL_PASSWORD connecthub_auth > auth_schema.sql
```

### Reset Database
```bash
docker-compose exec mysql mysql -uroot -p$MYSQL_PASSWORD -e "DROP DATABASE connecthub_auth; CREATE DATABASE connecthub_auth;"
```

---

## 🐛 Troubleshooting

### Service won't start
```bash
# Check logs
docker-compose logs auth-service

# Check dependencies
docker-compose ps  # Verify upstream services are healthy

# Restart service
docker-compose restart auth-service
```

### Database connection error
```bash
# Check MySQL health
docker-compose logs mysql

# Test connectivity
docker-compose exec auth-service curl -v http://mysql:3306

# Verify environment variables
docker-compose exec auth-service env | grep MYSQL
```

### Kafka/Redis connectivity issues
```bash
# Test Redis
docker-compose exec redis redis-cli ping

# Test Kafka
docker-compose exec kafka kafka-broker-api-versions.sh --bootstrap-server kafka:9092

# Check container IP
docker-compose exec auth-service nslookup kafka
```

### Port already in use
```bash
# Find process on port
lsof -i :8080

# Kill process
kill -9 <PID>

# Or use different compose file with different ports
docker-compose -f docker-compose.dev.yml up
```

### Out of memory
```bash
# Check resource usage
docker stats

# Increase Docker Desktop memory (Settings > Resources)
# Or stop non-essential services
docker-compose down
```

---

## 🔄 Common Operations

### Scale a service
```bash
# Note: Services are stateless, can run multiple instances
docker-compose up -d --scale auth-service=3
```

### Update environment variable
```bash
# Edit .env file
nano .env

# Restart service to apply changes
docker-compose restart auth-service
```

### View service metrics
```bash
docker stats auth-service

# Real-time monitoring
watch -n 1 'docker stats --no-stream'
```

### Backup database
```bash
docker-compose exec mysql mysqldump -uroot -p$MYSQL_PASSWORD --all-databases > backup.sql
```

### Restore database
```bash
docker-compose exec -T mysql mysql -uroot -p$MYSQL_PASSWORD < backup.sql
```

---

## 🚀 Performance Optimization

### Clear unused images and volumes
```bash
docker system prune -a
docker volume prune
```

### Check image sizes
```bash
docker images --format "table {{.Repository}}\t{{.Size}}"
```

### Monitor logs without storing them
```bash
# Limit log size in docker-compose.yml
# Add under each service:
logging:
  driver: "json-file"
  options:
    max-size: "10m"
    max-file: "3"
```

---

## ⚙️ Development Workflow

### Making code changes
```bash
# 1. Make changes in IDE
# 2. Rebuild JAR
mvn clean package -DskipTests -pl auth-service

# 3. Rebuild Docker image
docker-compose up --build auth-service

# 4. View logs
docker-compose logs -f auth-service
```

### Debugging
```bash
# Enter service container
docker-compose exec auth-service sh

# Inside container:
ps aux                              # View processes
cat /etc/os-release                # Check OS
ls -la /app/                        # Check JAR
java -version                       # Check Java version
```

### View environment variables
```bash
docker-compose exec auth-service env | sort
```

---

## 📈 Monitoring & Logs

### Centralized logging
```bash
# Save all logs to file
docker-compose logs > all_services.log

# Follow specific service
docker-compose logs -f --tail=100 auth-service

# Follow multiple services
docker-compose logs -f auth-service room-service message-service
```

### Admin Dashboard Monitoring
```
http://localhost:8089

Shows:
- All registered services
- Health status
- Memory usage
- Response times
- Custom metrics
```

### SonarQube Analysis
```
http://localhost:9000

Analyze code quality:
- Bugs and vulnerabilities
- Code coverage
- Code smells
- Duplications
```

---

## 🔐 Security Checks

### Verify no exposed secrets in logs
```bash
docker-compose logs | grep -i password
docker-compose logs | grep -i secret
docker-compose logs | grep -i token
```

### Check running processes
```bash
docker-compose exec auth-service ps aux
```

### Verify environment variables not exposed
```bash
docker-compose config | grep -i password
# Should show only placeholders like ${MYSQL_PASSWORD}
```

---

## 📝 Documentation Files

| File | Purpose |
|------|---------|
| `docker-compose.yml` | Main Docker Compose configuration |
| `.env.example` | Environment variables template |
| `DOCKER_SETUP.md` | Detailed setup documentation |
| `DOCKERIZATION_SUMMARY.md` | Complete implementation summary |
| `DOCKER_QUICK_REFERENCE.md` | This file - quick commands |

---

## 🎯 Emergency Procedures

### Complete reset
```bash
# Stop and remove everything
docker-compose down -v

# Remove images
docker-compose rm -f

# Clean Docker system
docker system prune -a

# Rebuild from scratch
docker-compose up --build
```

### Service hung/frozen
```bash
# Kill service
docker-compose kill auth-service

# Remove container
docker-compose rm -f auth-service

# Restart
docker-compose up -d auth-service
```

### Corrupted database
```bash
# Remove MySQL volume
docker volume rm connecthub_mysql_data

# Or via compose
docker-compose down -v

# Restart (fresh database)
docker-compose up -d mysql
```

---

## 📚 Further Reading

- Docker Documentation: https://docs.docker.com/
- Docker Compose Reference: https://docs.docker.com/compose/compose-file/
- Spring Boot Docker: https://spring.io/guides/gs/spring-boot-docker/
- Microservices Best Practices: https://microservices.io/
- Eureka Documentation: https://github.com/Netflix/eureka

---

**Last Updated:** May 15, 2026  
**Version:** 1.0.0
