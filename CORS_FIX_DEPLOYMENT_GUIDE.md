# CORS Fix - Deployment & Implementation Guide

## 📋 Quick Summary

**What**: Fixed CORS preflight OPTIONS requests returning 403 instead of 200  
**When**: Affects all cross-origin requests from frontend to backend  
**Impact**: CRITICAL - Frontend can't communicate with backend  
**Fix Size**: 2 files modified, ~15 lines of code added, 0 breaking changes  
**Status**: ✅ Ready for production deployment  

---

## 📁 Documentation Files

### In Root Directory (`/ConnectHub-backend/`)

1. **CORS_FIX_COMPLETE_REPORT.md**
   - Complete root cause analysis
   - Detailed explanation of the problem
   - Security implications
   - Testing procedures
   - Deployment checklist
   - **Read this**: For comprehensive understanding

2. **CORS_PREFLIGHT_FIX.md**
   - In-depth technical analysis
   - Filter execution order explained
   - Performance impact analysis
   - References and best practices
   - **Read this**: For technical deep dive

3. **CORS_FIX_SUMMARY.md**
   - Quick reference guide
   - Before/after comparison
   - Testing commands
   - Rollback procedures
   - **Read this**: Quick lookup during deployment

4. **CORS_FIX_VISUAL_GUIDE.md**
   - Visual flowcharts
   - Code comparisons
   - Network flow diagrams
   - Security checklist
   - **Read this**: For visual learners

---

## 🔧 Files Modified

### 1. JwtAuthenticationFilter.java
**Location**: `api-gateway/src/main/java/com/connecthub/gateway/filter/JwtAuthenticationFilter.java`

**Lines Added**: ~12 lines (110-126)

```java
// NEW CODE ADDED
@Override
public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    /*
     * CRITICAL FIX FOR CORS PREFLIGHT:
     * OPTIONS requests (CORS preflight) must bypass JWT validation entirely.
     */
    if (exchange.getRequest().getMethod().name().equals("OPTIONS")) {
        return chain.filter(exchange);
    }
    // ... rest of existing code ...
}
```

**Impact**: ✅ OPTIONS requests now pass through to CorsWebFilter

---

### 2. RateLimitFilter.java
**Location**: `api-gateway/src/main/java/com/connecthub/gateway/filter/RateLimitFilter.java`

**Lines Added**: ~10 lines (130-140)

```java
// NEW CODE ADDED
@Override
public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    /*
     * CORS PREFLIGHT FIX:
     * Skip rate limiting for OPTIONS requests (CORS preflight).
     */
    if (exchange.getRequest().getMethod().name().equals("OPTIONS")) {
        return chain.filter(exchange);
    }
    // ... rest of existing code ...
}
```

**Impact**: ✅ Defensive layer to ensure OPTIONS always passes

---

### 3. CorsConfig.java
**Location**: `api-gateway/src/main/java/com/connecthub/gateway/config/CorsConfig.java`

**Changes**: Enhanced documentation only (no functional changes)

**Impact**: ✅ Better maintainability, explains the fix dependency

---

## 🚀 Deployment Steps

### Pre-Deployment (Local Verification)

```bash
# 1. Pull latest changes
cd ConnectHub-backend
git pull origin main

# 2. Verify files were modified
git diff api-gateway/src/main/java/com/connecthub/gateway/filter/JwtAuthenticationFilter.java
git diff api-gateway/src/main/java/com/connecthub/gateway/filter/RateLimitFilter.java

# 3. Check for OPTIONS method check
grep -n "OPTIONS" api-gateway/src/main/java/com/connecthub/gateway/filter/JwtAuthenticationFilter.java
grep -n "OPTIONS" api-gateway/src/main/java/com/connecthub/gateway/filter/RateLimitFilter.java

# Expected: Both files should have an OPTIONS check
```

### Build

```bash
# Option 1: Build just the API Gateway
cd api-gateway
mvn clean package -DskipTests
# Expected: BUILD SUCCESS

# Option 2: Build entire project
cd ..
mvn clean package -DskipTests -am -pl api-gateway
# Expected: BUILD SUCCESS
```

### Test Locally (Docker Compose)

```bash
# 1. Start local stack (if not running)
docker-compose up -d

# 2. Wait for gateway to start
sleep 10

# 3. Test preflight request
curl -X OPTIONS \
  -H "Origin: http://localhost:4200" \
  -H "Access-Control-Request-Method: POST" \
  http://localhost:8080/api/v1/auth/login -v

# Expected output:
# HTTP/1.1 200 OK
# Access-Control-Allow-Origin: http://localhost:4200
# Access-Control-Allow-Methods: GET, POST, PUT, DELETE, PATCH, OPTIONS
# Access-Control-Allow-Headers: Authorization, Content-Type, ...
```

### Containerize & Push

```bash
# 1. Build Docker image
docker build -t connect-hub-api-gateway:v1.0.0 -f api-gateway/Dockerfile api-gateway/

# 2. Tag for registry
docker tag connect-hub-api-gateway:v1.0.0 registry.example.com/connect-hub-api-gateway:v1.0.0

# 3. Push to registry
docker push registry.example.com/connect-hub-api-gateway:v1.0.0
```

### Production Deployment

```bash
# 1. Update deployment manifest / docker-compose
# In production orchestrator (K8s, Swarm, etc.):
# Change image to: registry.example.com/connect-hub-api-gateway:v1.0.0

# 2. Deploy (varies by platform)
# For Docker Swarm:
docker service update --image registry.example.com/connect-hub-api-gateway:v1.0.0 api-gateway

# For Kubernetes:
kubectl set image deployment/api-gateway api-gateway=registry.example.com/connect-hub-api-gateway:v1.0.0

# For AWS ECS, Google Cloud Run, etc. - use respective CLIs
```

---

## ✅ Post-Deployment Verification

### Step 1: Check Deployment Status

```bash
# Verify service is running
curl https://api.connect-hub.dev/actuator/health

# Expected: { "status": "UP" }
```

### Step 2: Test CORS Preflight from Production

```bash
# Test preflight request
curl -X OPTIONS \
  -H "Origin: https://connect-hub-frontend-one.vercel.app" \
  -H "Access-Control-Request-Method: POST" \
  -H "Access-Control-Request-Headers: Authorization,Content-Type" \
  https://api.connect-hub.dev/api/v1/auth/login \
  -v 2>&1 | grep -E "(< HTTP|Access-Control)"

# Expected:
# < HTTP/2 200
# < access-control-allow-origin: https://connect-hub-frontend-one.vercel.app
# < access-control-allow-methods: GET, POST, PUT, DELETE, PATCH, OPTIONS
```

### Step 3: Test Actual Request from Frontend

```bash
# In browser console, from frontend domain:
fetch('https://api.connect-hub.dev/api/v1/auth/login', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'Authorization': 'Bearer test-token'
  },
  body: JSON.stringify({
    email: 'test@example.com',
    password: 'password'
  })
})
.then(r => r.json())
.then(console.log)
.catch(console.error)

# Expected: 
# - No CORS errors in console
# - Response received (even if 401 Unauthorized due to bad token)
```

### Step 4: Monitor Logs

```bash
# Check for any OPTIONS-related errors
kubectl logs -f deployment/api-gateway | grep -i "option\|cors"

# Expected: Minimal output, no error patterns like:
# ❌ "403 Forbidden"
# ❌ "CORS"
# ❌ "preflight"
```

### Step 5: Run Full Test Suite

```bash
# Test all three frontend origins
for origin in \
  "https://connect-hub-frontend-one.vercel.app" \
  "https://connect-hub-frontend-git-main-mohit-d360s-projects.vercel.app" \
  "http://localhost:4200"; do
  
  echo "Testing origin: $origin"
  curl -X OPTIONS \
    -H "Origin: $origin" \
    -H "Access-Control-Request-Method: POST" \
    https://api.connect-hub.dev/api/v1/auth/login \
    -s -o /dev/null -w "Status: %{http_code}\n"
  
  # Expected: Status: 200 for all three
done
```

---

## 🔄 Rollback Instructions

If critical issues occur:

```bash
# 1. Identify previous stable version
git log --oneline api-gateway/ | head -5

# 2. Revert changes
git revert --no-edit <commit-hash>

# 3. Rebuild
cd api-gateway
mvn clean package -DskipTests

# 4. Rebuild Docker image
docker build -t connect-hub-api-gateway:rollback -f Dockerfile .

# 5. Redeploy rollback version
# (Use same deployment method as above, with rollback image)

# 6. Verify rollback
curl https://api.connect-hub.dev/actuator/health
```

---

## 🛡️ Security Verification

### Before Deploying to Production

```bash
# 1. Verify OPTIONS is secured (can't execute logic)
curl -X OPTIONS https://api.connect-hub.dev/api/v1/auth/login -v
# Must return: 200 OK (no error, but also no data)

# 2. Verify JWT is still required for real requests
curl -X POST https://api.connect-hub.dev/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{}' -v
# Must return: 401 Unauthorized

# 3. Verify valid JWT works
curl -X POST https://api.connect-hub.dev/api/v1/auth/login \
  -H "Authorization: Bearer $VALID_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"email":"test@test.com"}' -v
# Should return: 200 or 401 (depending on credentials), NOT 403

# 4. Verify rate limiting still works
for i in {1..10}; do
  curl -s -X POST https://api.connect-hub.dev/api/v1/auth/login/email/request-otp \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"email":"test@test.com"}' | jq .
done
# After 5 OTP requests, should get 429 Too Many Requests
```

---

## 📊 Monitoring & Alerting

### Add to Monitoring Dashboard

```
Metrics to track:
- OPTIONS request count (should match non-200 rate)
- OPTIONS response time (should be < 100ms)
- CORS error rate (should be 0%)
- JWT validation errors (for non-OPTIONS requests)
- Rate limit violations (should continue normally)
```

### Add Alerts

```
Alert if:
- CORS error rate > 0%              (indicate new issue)
- OPTIONS response time > 500ms     (indicate performance issue)
- Preflight success rate < 99%      (indicate deployment issue)
- JWT validation errors spike       (unrelated, but monitor)
```

---

## 🔍 Troubleshooting

### Symptom: Still Getting CORS Errors After Deployment

```
Check:
1. Confirm deployment succeeded
   kubectl get pods -l app=api-gateway
   → Should show running pod

2. Verify code was deployed
   kubectl logs deployment/api-gateway | grep "OPTIONS"
   → Should show new OPTIONS check code being executed

3. Check frontend origin is whitelisted
   grep "connect-hub-frontend" api-gateway/src/main/java/.../CorsConfig.java
   → Must include all three production URLs

4. Verify CORS filter is running
   curl https://api.connect-hub.dev/api/v1/auth/login -v 2>&1 | grep -i cors
   → Should show CORS headers in response
```

### Symptom: JWT Requests Now Failing

```
This is BAD - indicates something broke JWT flow:

1. Check logs
   kubectl logs deployment/api-gateway | grep -i "jwt\|unauthorized"

2. Verify JWT secret hasn't changed
   echo $JWT_SECRET | wc -c
   → Should be same length as before

3. Check if non-OPTIONS requests are reaching JWT filter
   kubectl logs deployment/api-gateway | grep "POST" | grep -v OPTIONS

4. Rollback immediately if unsure
   git revert --no-edit <commit-hash>
```

---

## 📝 Deployment Checklist

Before Deployment:
- [ ] Code reviewed and tested locally
- [ ] Build succeeds: `mvn clean package`
- [ ] All three frontend origins are in CorsConfig
- [ ] OPTIONS check added to both JwtAuthenticationFilter and RateLimitFilter
- [ ] No other files modified

During Deployment:
- [ ] Build Docker image
- [ ] Push to registry
- [ ] Update deployment manifest with new image
- [ ] Deploy via orchestrator
- [ ] Wait for pods to be ready
- [ ] Check service health

After Deployment:
- [ ] Health check passes
- [ ] OPTIONS requests return 200
- [ ] CORS headers present in responses
- [ ] JWT still required for actual requests
- [ ] Rate limiting still enforced
- [ ] Frontend can successfully make requests
- [ ] No errors in logs
- [ ] Monitoring metrics look normal

---

## 📞 Support & Questions

### If You Need Help

1. **Check the docs**:
   - CORS_FIX_COMPLETE_REPORT.md (comprehensive)
   - CORS_FIX_SUMMARY.md (quick reference)
   - CORS_FIX_VISUAL_GUIDE.md (diagrams and visuals)

2. **Review the code**:
   - Lines 110-126 in JwtAuthenticationFilter.java
   - Lines 130-140 in RateLimitFilter.java

3. **Test locally first**:
   - Run Docker Compose locally
   - Test with curl commands provided
   - Check logs for any issues

4. **Escalation path**:
   - Check logs: `docker logs api-gateway`
   - Review changes: `git diff` against previous version
   - Rollback if needed: Follow rollback instructions
   - Escalate to team lead if still failing

---

## ✨ Success Criteria

```
✅ OPTIONS requests return 200 OK
✅ CORS headers present in response
✅ All three frontend origins allowed
✅ JWT still required for actual requests
✅ Rate limiting still enforced
✅ No breaking changes to existing endpoints
✅ Frontend can successfully make requests
✅ No CORS errors in browser console
✅ Monitoring shows normal metrics
✅ Logs show no errors
```

Once all criteria are met, deployment is complete and successful!

