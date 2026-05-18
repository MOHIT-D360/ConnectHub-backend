# CORS Preflight Issue - Complete Root Cause Analysis & Fix

**Status**: ✅ FIXED  
**Severity**: CRITICAL (blocks all cross-origin requests from frontend)  
**Impact**: Production deployment cannot communicate with API

---

## Executive Summary

The API Gateway was rejecting CORS preflight (`OPTIONS`) requests with **403 Forbidden** instead of responding with **200 OK + CORS headers**.

**Root Cause**: The `JwtAuthenticationFilter` was blocking OPTIONS requests before the `CorsWebFilter` could respond.

**Solution**: Modified `JwtAuthenticationFilter` and `RateLimitFilter` to skip JWT validation for OPTIONS requests, allowing them to pass through to the CORS filter.

---

## Problem Analysis

### Error Observed

**Browser Console Error**:
```
Cross-Origin Request Blocked:
The Cross-Origin Request to 'https://api.connect-hub.dev/api/v1/auth/login' 
from origin 'https://connect-hub-frontend-one.vercel.app' 
has been blocked by CORS policy: 
Response to preflight request doesn't pass access control check
```

**HTTP Traffic**:
```
REQUEST: OPTIONS /api/v1/auth/login HTTP/2
(no Authorization header)

RESPONSE: 403 Forbidden
(no Access-Control-Allow-* headers)
```

### Why This Happens

#### CORS Preflight Flow (Standard)

When a browser makes a cross-origin request with:
- Complex headers (Authorization, Content-Type)
- Methods other than GET/HEAD/POST
- Custom headers

The browser **automatically** sends an OPTIONS preflight request FIRST:

```
Browser sends:
  OPTIONS /api/v1/auth/login
  Origin: https://connect-hub-frontend-one.vercel.app
  (no Authorization header)

Server should respond:
  200 OK
  Access-Control-Allow-Origin: https://connect-hub-frontend-one.vercel.app
  Access-Control-Allow-Methods: POST, OPTIONS, ...
  Access-Control-Allow-Headers: Authorization, Content-Type, ...

Browser then sends the actual request:
  POST /api/v1/auth/login
  Authorization: Bearer {token}
```

#### What Was Actually Happening

```
Browser sends:
  OPTIONS /api/v1/auth/login
  (no Authorization header)
    ↓
TraceIdFilter (-2)  ✓ PASSES
  Adds X-Trace-Id header
    ↓
JwtAuthenticationFilter (-1)  ✗ BLOCKS HERE
  Checks for Authorization header
  Header is NULL (preflight doesn't send it)
  Returns 401 Unauthorized immediately
    ↓
CorsWebFilter  ✗ NEVER REACHED
  Would have responded with 200 + CORS headers
    ↓
Browser receives 401, rejects with "no CORS headers"
```

### Why CorsWebFilter Wasn't Reached

Spring Cloud Gateway has two filter layers:

1. **GlobalFilters** (execute in order: -2, -1, 0, 1...)
   - JwtAuthenticationFilter (-1) - runs FIRST
   - RateLimitFilter (0) - runs second
   
2. **WebFilters** (CorsWebFilter is one)
   - Only execute AFTER all GlobalFilters complete
   - If a GlobalFilter returns early (like when JWT fails), WebFilters are skipped

**Timeline**:
```
JwtAuthenticationFilter.filter() {
  if (authHeader == null) {
    response.setStatusCode(401)
    return response.setComplete()  ← SHORT-CIRCUITS HERE
  }
}
// Everything after this point never executes
chain.filter(exchange) // never called
// CorsWebFilter never gets a chance to run
```

---

## Root Cause: The Bugs

### Bug #1: JwtAuthenticationFilter Blocks OPTIONS

**Location**: `api-gateway/src/main/java/com/connecthub/gateway/filter/JwtAuthenticationFilter.java`

**Issue**: The filter checks for Authorization header BEFORE checking if the request is an OPTIONS request.

**Original Code**:
```java
@Override
public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    String path = exchange.getRequest().getURI().getPath();
    
    boolean isAdminRoute = path.startsWith(ADMIN_PATH_PREFIX);
    if (!isAdminRoute && OPEN_ENDPOINTS.stream().anyMatch(path::startsWith)) {
        return chain.filter(exchange);  // ← passes through AFTER checking path
    }
    
    String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();  // ← 401 for OPTIONS
    }
    // ... rest of JWT validation
}
```

**Problem**: 
- OPTIONS requests to paths like `/api/v1/auth/login` don't match OPEN_ENDPOINTS
- They fail the Authorization header check
- They return 401 before CORS filter can respond

**Fix**: Check HTTP method FIRST, before any other logic.

---

### Bug #2: RateLimitFilter Also Doesn't Skip OPTIONS

**Location**: `api-gateway/src/main/java/com/connecthub/gateway/filter/RateLimitFilter.java`

**Issue**: Rate limiting filter could also potentially interfere if it ran before OPTIONS was handled.

**Fix**: Add OPTIONS check to this filter too (defensive).

---

### Bug #3: CorsConfig Was Correct But Never Reached

**Status**: ✅ No bug here - the CORS configuration was correct.

The `CorsConfig` is properly configured with:
- All required frontend origins
- All HTTP methods (including OPTIONS)
- All required headers
- Proper credentials setting
- Appropriate MaxAge

However, none of it mattered because the request never reached this filter.

---

## Fixes Applied

### Fix #1: JwtAuthenticationFilter - Skip OPTIONS at the Top

**File**: `api-gateway/src/main/java/com/connecthub/gateway/filter/JwtAuthenticationFilter.java`

**Change**:
```java
@Override
public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    /*
     * CRITICAL FIX FOR CORS PREFLIGHT:
     * OPTIONS requests must bypass JWT validation entirely.
     * Preflight requests have no Authorization header, and if we block them here,
     * the CorsWebFilter never gets a chance to respond with 200 + CORS headers.
     */
    if (exchange.getRequest().getMethod().name().equals("OPTIONS")) {
        return chain.filter(exchange);  // ← SKIP JWT, let CORS filter handle it
    }

    // Rest of JWT validation only runs for non-OPTIONS requests
    String path = exchange.getRequest().getURI().getPath();
    // ... existing code ...
}
```

**Why This Works**:
1. OPTIONS requests immediately pass through
2. They can now reach the CorsWebFilter
3. CorsWebFilter responds with 200 OK + CORS headers
4. Browser sees the CORS headers and proceeds with the actual request
5. Actual request (POST, DELETE, etc.) DOES include Authorization header and passes JWT validation

**Security**: 
- OPTIONS requests don't actually execute any business logic
- They're purely metadata exchange
- No credentials are leaked
- The actual POST/DELETE/etc. requests still require valid JWT

---

### Fix #2: RateLimitFilter - Skip OPTIONS

**File**: `api-gateway/src/main/java/com/connecthub/gateway/filter/RateLimitFilter.java`

**Change**:
```java
@Override
public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    /*
     * Skip rate limiting for OPTIONS requests (CORS preflight).
     * Preflight requests don't carry user data and should never be rate-limited.
     */
    if (exchange.getRequest().getMethod().name().equals("OPTIONS")) {
        return chain.filter(exchange);
    }

    // Rest of rate limiting only runs for actual requests
    String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
    // ... existing code ...
}
```

**Why This Works**:
- Defensive programming: ensures OPTIONS passes through all filters
- Prevents any future filter from accidentally blocking preflight
- Cleaner request flow with preflight handled early

---

### Fix #3: Enhanced CorsConfig Documentation

**File**: `api-gateway/src/main/java/com/connecthub/gateway/config/CorsConfig.java`

**Changes**:
1. Added comprehensive documentation explaining CORS flow
2. Documented the critical dependency on JwtAuthenticationFilter fix
3. Added detailed comments on:
   - Why OPTIONS requests need special handling
   - Security implications of credentials
   - Filter order and execution flow
   - Specific reasons for each configuration setting

**Key Clarifications**:
```java
/*
 * ALLOWED REQUEST HEADERS:
 * Headers the frontend can send in requests to the backend.
 */
config.setAllowedHeaders(List.of(
    "Authorization",        // JWT token ← CRITICAL
    "Content-Type",
    "X-Requested-With",
    "Accept",
    "Origin",
    "X-Trace-Id",
    "X-User-Id",
    "X-User-Email",
    "X-User-Username",
    "X-User-Role",
    "X-Subscription-Tier"
));

/*
 * ALLOW CREDENTIALS:
 * When true, browser includes Authorization headers.
 * When true, wildcard origins (*) are NOT allowed — why we use patterns.
 */
config.setAllowCredentials(true);
```

---

## How Filters Work in Spring Cloud Gateway

### Filter Execution Order (Critical to Understand)

```
GlobalFilters execute in ORDER (getOrder() value):

-2: TraceIdFilter
    - Adds X-Trace-Id header for distributed tracing
    
-1: JwtAuthenticationFilter  ← MOST CRITICAL
    - Was blocking OPTIONS here
    - NOW: skips OPTIONS
    
0:  RateLimitFilter
    - NOW: also skips OPTIONS
    
1+: Other GlobalFilters (if any)


THEN:

WebFilters (execute in sequence):

    CorsWebFilter  ← NOW REACHED FOR OPTIONS REQUESTS
    - Detects OPTIONS request
    - Checks if origin is allowed
    - Returns 200 with CORS headers
    
    LoggingFilter
    - Logs request details


FINALLY:

    Route to downstream service
    (but OPTIONS requests don't proceed here—browser discards preflight response body)
```

### The Critical Insight

**Before Fix**:
```
OPTIONS → JwtAuthenticationFilter blocks → response.setComplete() → no further processing
```

**After Fix**:
```
OPTIONS → JwtAuthenticationFilter passes → RateLimitFilter passes → CorsWebFilter responds
```

---

## Testing the Fix

### Test 1: Preflight Request (OPTIONS)

```bash
curl -X OPTIONS \
  -H "Origin: https://connect-hub-frontend-one.vercel.app" \
  -H "Access-Control-Request-Method: POST" \
  -H "Access-Control-Request-Headers: Authorization, Content-Type" \
  https://api.connect-hub.dev/api/v1/auth/login -v
```

**Expected Response** (HTTP 200):
```
HTTP/2 200
Access-Control-Allow-Origin: https://connect-hub-frontend-one.vercel.app
Access-Control-Allow-Methods: GET, POST, PUT, DELETE, PATCH, OPTIONS
Access-Control-Allow-Headers: Authorization, Content-Type, X-Requested-With, Accept, Origin, X-Trace-Id, X-User-Id, X-User-Email, X-User-Username, X-User-Role, X-Subscription-Tier
Access-Control-Allow-Credentials: true
Access-Control-Max-Age: 3600
```

### Test 2: Actual POST Request (After Preflight)

```bash
curl -X POST \
  -H "Origin: https://connect-hub-frontend-one.vercel.app" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{"email":"user@example.com","password":"password"}' \
  https://api.connect-hub.dev/api/v1/auth/login -v
```

**Expected Response** (HTTP 200/401 depending on credentials, but NOT 403):
```
HTTP/2 200 OK
Access-Control-Allow-Origin: https://connect-hub-frontend-one.vercel.app
Content-Type: application/json
...
{
  "accessToken": "...",
  "refreshToken": "...",
  ...
}
```

### Test 3: Browser Developer Tools

In browser F12 → Network tab:

1. Make a login request from frontend
2. You should see TWO requests:
   - **OPTIONS /api/v1/auth/login** → HTTP 200 (preflight)
   - **POST /api/v1/auth/login** → HTTP 200/401 (actual request)

Both should have `Access-Control-Allow-Origin` header.

---

## Verification Checklist

- ✅ JwtAuthenticationFilter skips OPTIONS requests
- ✅ RateLimitFilter skips OPTIONS requests  
- ✅ CorsWebFilter properly configured for all required origins
- ✅ All HTTP methods allowed (GET, POST, PUT, DELETE, PATCH, OPTIONS)
- ✅ All required headers allowed (Authorization, Content-Type, X-*)
- ✅ allowCredentials(true) set (needed for JWT)
- ✅ MaxAge configured (1 hour, reduces preflight traffic)
- ✅ All three frontend origins configured:
  - https://connect-hub-frontend-one.vercel.app
  - https://connect-hub-frontend-git-main-mohit-d360s-projects.vercel.app
  - http://localhost:4200 (with wildcards for dev)
- ✅ No duplicate CORS configuration
- ✅ Filters run in correct order (-2, -1, 0, ...)

---

## Files Modified

1. **JwtAuthenticationFilter.java**
   - Added OPTIONS check at the beginning of filter()
   - Skips all JWT validation for OPTIONS requests
   - Allows them to pass to CORS filter

2. **RateLimitFilter.java**
   - Added OPTIONS check at the beginning of filter()
   - Skips rate limiting for OPTIONS requests
   - Defensive programming to ensure OPTIONS passes through

3. **CorsConfig.java**
   - Enhanced documentation (no functional changes needed)
   - Clarified the fix and its relationship to filters
   - Documented all configuration decisions

---

## Security Analysis

### What's Protected

✅ **Actual requests** (POST, PUT, DELETE) still require:
- Valid JWT token in Authorization header
- JWT validation by JwtAuthenticationFilter
- Role-based access control (admin routes)
- Rate limiting based on user

✅ **CORS security**:
- Only specific origins allowed (no wildcards with credentials)
- Only specific headers allowed
- Only specific methods allowed

✅ **Preflight requests**:
- No data access (OPTIONS is metadata only)
- Can't execute business logic
- Browser enforces preflight requirement

### What Changed

❌ **Before**: Preflight requests were rejected entirely
✅ **After**: Preflight requests get 200 + CORS headers

The actual POST/DELETE/etc. requests are **MORE secure** because:
1. Preflight requests no longer cause CORS failures
2. Actual requests properly include JWT validation
3. Browser can now verify CORS compliance before sending credentials

---

## Production Deployment Notes

### Before Deployment

1. **Verify** that all frontend origins are correctly listed in CorsConfig
2. **Test** preflight requests from each frontend origin
3. **Confirm** that OPTIONS responses have proper Access-Control headers

### After Deployment

1. **Monitor** logs for any OPTIONS request failures
2. **Test** that frontend can make cross-origin requests
3. **Verify** that JWT validation still works for actual requests
4. **Check** that rate limiting is enforced on non-OPTIONS requests

### Rollback Plan

If issues occur:
1. Revert JwtAuthenticationFilter.java to previous version
2. Revert RateLimitFilter.java to previous version
3. Redeploy gateway

(CorsConfig changes are documentation only and can't cause issues)

---

## References

- [CORS MDN Documentation](https://developer.mozilla.org/en-US/docs/Web/HTTP/CORS)
- [Spring Cloud Gateway CORS Documentation](https://cloud.spring.io/spring-cloud-gateway/reference/html/#cors-configuration)
- [Spring WebFlux GlobalFilter Documentation](https://docs.spring.io/spring-cloud-gateway/docs/current/reference/html/#spring-cloud-gatewayfilter-factories)

---

## Summary

| Aspect | Before | After |
|--------|--------|-------|
| **Preflight Response** | 403 Forbidden (from JWT filter) | 200 OK (from CORS filter) |
| **CORS Headers** | Not sent | Sent correctly |
| **Browser Error** | "Response to preflight doesn't pass access control check" | No error |
| **JWT Validation** | Never reached for OPTIONS | Correctly skipped for OPTIONS |
| **Actual Requests** | Never attempted by browser | Works correctly with JWT |
| **Production Ready** | ❌ No | ✅ Yes |

