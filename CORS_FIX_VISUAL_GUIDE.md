# CORS Fix - Visual Reference Guide

## Problem vs Solution

### BEFORE FIX: CORS Preflight Blocked

```
┌─────────────────────────────────────────────────────┐
│ Frontend Browser (Chrome/Firefox)                   │
│ https://connect-hub-frontend-one.vercel.app        │
│                                                     │
│ User clicks "Login" button                          │
│ Browser needs to send: POST /api/v1/auth/login      │
│ With header: Authorization: Bearer {token}         │
└────────────────────┬────────────────────────────────┘
                     │
         Automatic CORS Preflight (Browser's Job)
                     │
                     ▼
┌─────────────────────────────────────────────────────┐
│ OPTIONS /api/v1/auth/login                          │
│ Origin: https://connect-hub-frontend-one.vercel.app│
│ Access-Control-Request-Method: POST                 │
│ Access-Control-Request-Headers: Authorization      │
│                                                     │
│ (Browser: "Server, can I send a POST with this     │
│  header from this origin?")                        │
└────────────────────┬────────────────────────────────┘
                     │
                     ▼
         ❌ API GATEWAY (BROKEN)
┌─────────────────────────────────────────────────────┐
│ JwtAuthenticationFilter                             │
│                                                     │
│ Check: Authorization header exists?                │
│ Result: NO (preflight has no Authorization)        │
│                                                     │
│ Response: 403 Forbidden                            │
│ (No CORS headers!)                                 │
└────────────────────┬────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────┐
│ Browser Console Error                              │
│                                                     │
│ ❌ CORS policy: Response to preflight request     │
│    doesn't pass access control check               │
│                                                     │
│ Response to preflight request doesn't pass         │
│ access control check: No 'Access-Control-Allow-   │
│ Origin' header is present on the requested         │
│ resource.                                          │
└─────────────────────────────────────────────────────┘
```

---

### AFTER FIX: CORS Preflight Works

```
┌─────────────────────────────────────────────────────┐
│ Frontend Browser (Chrome/Firefox)                   │
│ https://connect-hub-frontend-one.vercel.app        │
│                                                     │
│ User clicks "Login" button                          │
│ Browser needs to send: POST /api/v1/auth/login      │
│ With header: Authorization: Bearer {token}         │
└────────────────────┬────────────────────────────────┘
                     │
         Automatic CORS Preflight (Browser's Job)
                     │
                     ▼
┌─────────────────────────────────────────────────────┐
│ OPTIONS /api/v1/auth/login                          │
│ Origin: https://connect-hub-frontend-one.vercel.app│
│ Access-Control-Request-Method: POST                 │
│ Access-Control-Request-Headers: Authorization      │
│                                                     │
│ (Browser: "Server, can I send a POST with this     │
│  header from this origin?")                        │
└────────────────────┬────────────────────────────────┘
                     │
                     ▼
         ✅ API GATEWAY (FIXED!)
┌─────────────────────────────────────────────────────┐
│ TraceIdFilter (-2)                                  │
│ ✓ Adds X-Trace-Id for distributed tracing          │
└────────────────────┬────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────┐
│ JwtAuthenticationFilter (-1)  ← THE FIX HERE        │
│                                                     │
│ Check: Is this an OPTIONS request?                 │
│ Result: YES (NEW CHECK ADDED)                      │
│                                                     │
│ Action: PASS THROUGH (skip JWT validation)         │
│ ✓ Allows CorsWebFilter to handle response          │
└────────────────────┬────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────┐
│ RateLimitFilter (0)  ← ALSO SKIPS OPTIONS           │
│                                                     │
│ Check: Is this an OPTIONS request?                 │
│ Result: YES (NEW CHECK ADDED)                      │
│                                                     │
│ Action: PASS THROUGH (no rate limit needed)        │
└────────────────────┬────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────┐
│ CorsWebFilter  ← NOW REACHED!                       │
│                                                     │
│ Check: Is origin allowed?                          │
│ ✓ Yes: connect-hub-frontend-one.vercel.app         │
│                                                     │
│ Check: Is method allowed?                          │
│ ✓ Yes: POST is in allowed methods                  │
│                                                     │
│ Check: Are headers allowed?                        │
│ ✓ Yes: Authorization, Content-Type, etc.          │
│                                                     │
│ Response:                                          │
│ HTTP 200 OK                                        │
│ Access-Control-Allow-Origin: ...vercel.app         │
│ Access-Control-Allow-Methods: GET, POST, ...       │
│ Access-Control-Allow-Headers: Authorization, ...   │
│ Access-Control-Allow-Credentials: true             │
│ Access-Control-Max-Age: 3600                       │
└────────────────────┬────────────────────────────────┘
                     │
         Browser receives CORS headers ✓
         Checks: "Yes, I can send POST!"
                     │
                     ▼
┌─────────────────────────────────────────────────────┐
│ POST /api/v1/auth/login                             │
│ Authorization: Bearer {actual_token}                │
│ Content-Type: application/json                      │
│ {user_credentials}                                  │
│                                                     │
│ (Browser: "Server said I can send this,             │
│  here's the actual request")                       │
└────────────────────┬────────────────────────────────┘
                     │
                     ▼
         ✅ API GATEWAY (Full Processing)
┌─────────────────────────────────────────────────────┐
│ TraceIdFilter → JwtAuthenticationFilter →           │
│ RateLimitFilter (all work normally)                 │
│                                                     │
│ Response: 200 OK / 401 Unauthorized                 │
│ (with CORS headers)                                │
└────────────────────┬────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────┐
│ Browser                                             │
│                                                     │
│ ✅ CORS check passed!                              │
│ ✅ Response received!                              │
│ ✅ Login successful!                               │
│                                                     │
│ (No CORS errors in console)                        │
└─────────────────────────────────────────────────────┘
```

---

## Filter Execution Order (Critical!)

### GlobalFilters Execute First (in order)

```
Request arrives at API Gateway
                │
                ▼
    GlobalFilters (in order, -2 first, highest priority)
    
    ┌──────────────────────────────┐
    │ -2: TraceIdFilter            │  Always executes
    │ • Adds X-Trace-Id header     │  (1st priority)
    └──────────┬───────────────────┘
               │
               ▼
    ┌──────────────────────────────┐
    │ -1: JwtAuthenticationFilter  │  Always executes
    │ • [NEW] Checks for OPTIONS   │  (2nd priority)
    │ • If OPTIONS → PASS THROUGH  │
    │ • Else → Validate JWT        │  ← THE FIX
    └──────────┬───────────────────┘
               │
               ▼
    ┌──────────────────────────────┐
    │ 0: RateLimitFilter           │  Always executes
    │ • [NEW] Checks for OPTIONS   │  (3rd priority)
    │ • If OPTIONS → PASS THROUGH  │
    │ • Else → Apply rate limit    │  ← DEFENSIVE FIX
    └──────────┬───────────────────┘
               │
               ▼
    If any filter returns early:
    └──────────┬───────────────────┐
               │                   │
          chain.filter() called    response.setComplete()
          (continue)               (stop here, return response)
               │                   │
               ▼                   ▼
```

### WebFilters Execute After All GlobalFilters

```
After ALL GlobalFilters complete:
                │
                ▼
    WebFilters (Reactive chain)
    
    ┌──────────────────────────────┐
    │ CorsWebFilter                │  
    │ • Adds CORS headers to resp  │  ← NOW REACHED FOR OPTIONS!
    │ • Responds to preflight (200)│     (Before fix: never reached)
    └──────────┬───────────────────┘
               │
               ▼
    ┌──────────────────────────────┐
    │ Other WebFilters             │
    │ • LoggingFilter              │
    └──────────┬───────────────────┘
               │
               ▼
    Route to Downstream Service
    (Auth-Service, Room-Service, etc.)
```

---

## Code Change Comparison

### JwtAuthenticationFilter - What Changed

```java
// BEFORE (Broken)
@Override
public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    String path = exchange.getRequest().getURI().getPath();
    
    boolean isAdminRoute = path.startsWith(ADMIN_PATH_PREFIX);
    if (!isAdminRoute && OPEN_ENDPOINTS.stream().anyMatch(path::startsWith)) {
        return chain.filter(exchange);
    }
    
    // ❌ OPTIONS requests reach here without Authorization header
    String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();  // ← 403 for OPTIONS
    }
    // ...
}

// AFTER (Fixed)
@Override
public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    // ✅ NEW: Check for OPTIONS FIRST, before any other logic
    if (exchange.getRequest().getMethod().name().equals("OPTIONS")) {
        return chain.filter(exchange);  // ← Pass through to CORS filter
    }
    
    String path = exchange.getRequest().getURI().getPath();
    
    boolean isAdminRoute = path.startsWith(ADMIN_PATH_PREFIX);
    if (!isAdminRoute && OPEN_ENDPOINTS.stream().anyMatch(path::startsWith)) {
        return chain.filter(exchange);
    }
    
    // OPTIONS requests never reach here anymore
    String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }
    // ...
}
```

---

## Testing Flow

### Browser Network Tab (DevTools F12)

```
Before Fix:
├─ OPTIONS /api/v1/auth/login
│  └─ Status: 403 Forbidden ❌
│     No CORS headers ❌
│     Browser blocks request ❌
│
└─ (Never sends actual POST)

After Fix:
├─ OPTIONS /api/v1/auth/login (preflight)
│  └─ Status: 200 OK ✓
│     Access-Control-Allow-Origin: https://... ✓
│     Access-Control-Allow-Methods: POST ✓
│
└─ POST /api/v1/auth/login (actual)
   └─ Status: 200 OK / 401 Unauthorized ✓
      (No CORS errors, request succeeded)
```

---

## Quick Diagnosis

### If You Still See CORS Errors After Fix

```
Problem                    Check
──────────────────────────────────────────────
OPTIONS → 404             • Is gateway running?
                          • Is route defined?

OPTIONS → 403             • Did fix get deployed?
                          • Is code change present?

OPTIONS → 200 but          • Check CORS header values
actual POST still fails    • Is origin whitelisted?
                          • Are headers in whitelist?
                          • Check browser console
```

---

## Fallback / Rollback

```bash
# If deployment fails:

# 1. Revert changes
git checkout api-gateway/src/main/java/com/connecthub/gateway/filter/JwtAuthenticationFilter.java
git checkout api-gateway/src/main/java/com/connecthub/gateway/filter/RateLimitFilter.java

# 2. Rebuild
mvn -f api-gateway/pom.xml clean package

# 3. Redeploy
docker build -t connect-hub-api-gateway:latest -f api-gateway/Dockerfile api-gateway/

# 4. Restart container
docker-compose restart api-gateway

# 5. Verify fix reverted
curl -X OPTIONS https://api.connect-hub.dev/api/v1/auth/login -v
# Should be back to old behavior (403 or same as before)
```

---

## Security Checklist

✅ OPTIONS requests:
  - No Authorization header required
  - No rate limiting applied
  - No business logic executed
  - Browser discards response body

✅ POST/PUT/DELETE requests:
  - Authorization header REQUIRED
  - JWT signature validated
  - User identity extracted
  - Rate limiting applied
  - Admin roles checked

✅ CORS Security:
  - Only specific origins allowed
  - No wildcards with credentials
  - Only specific methods allowed
  - Only specific headers allowed

---

## Files Changed

```
api-gateway/
├── src/main/java/com/connecthub/gateway/
│   ├── filter/
│   │   ├── JwtAuthenticationFilter.java          ← FIXED (OPTIONS check)
│   │   └── RateLimitFilter.java                  ← FIXED (OPTIONS check)
│   └── config/
│       └── CorsConfig.java                       ← Enhanced docs only
└── [other files unchanged]
```

---

## Success Criteria

```
✅ CORS Preflight Works
   • OPTIONS returns 200 OK
   • CORS headers present
   • Actual request sent

✅ JWT Security Intact
   • Valid JWT → access granted
   • Invalid JWT → 401 Unauthorized
   • No JWT → 401 Unauthorized

✅ Rate Limiting Works
   • Rate limit headers present
   • Requests above limit → 429

✅ Admin Routes Protected
   • Admin role required
   • Non-admin JWT → 403 Forbidden

✅ No Breaking Changes
   • All existing endpoints work
   • All existing clients work
   • All existing logic unchanged
```

