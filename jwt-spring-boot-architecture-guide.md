# JWT Authentication in Spring Boot 3.x / Spring Security 6.x
### A Production-Grade Architectural Guide

---

## 1. Conceptual Framework & Flow

### 1.1 Why Stateless JWT Auth?

In a traditional session-based model, the server stores session state (in memory, Redis, or a DB) and the client holds a session ID cookie. This doesn't scale horizontally without a shared session store.

With **JWT**, the server stores nothing. The token itself is a signed, self-contained proof of identity. Any server instance can validate it independently as long as it knows the signing secret/public key. This is what makes it ideal for microservices and horizontally-scaled REST APIs.

The trade-off: since the server doesn't "remember" the token, **revocation** (logout, ban a user) requires extra engineering (see Section 4).

### 1.2 The Two Distinct Flows

**Flow A — Authentication (Login): Issuing a token**

```
┌────────┐        POST /api/auth/login             ┌──────────────────┐
│ Client │ ───────{username, password}────────────▶ │  AuthController   │
└────────┘                                           └────────┬──────────┘
                                                               │
                                                               ▼
                                                  ┌─────────────────────────┐
                                                  │  AuthenticationManager   │
                                                  │ (delegates to Provider)  │
                                                  └────────────┬────────────┘
                                                               │
                                                               ▼
                                                  ┌─────────────────────────┐
                                                  │   UserDetailsService     │
                                                  │  loadUserByUsername()    │
                                                  └────────────┬────────────┘
                                                               │
                                                               ▼
                                                  ┌─────────────────────────┐
                                                  │        Database          │
                                                  │  (fetch user + hash)     │
                                                  └────────────┬────────────┘
                                                               │
                                              PasswordEncoder.matches()
                                                               │
                                                     ✅ credentials valid
                                                               │
                                                               ▼
                                                  ┌─────────────────────────┐
                                                  │      JwtUtils            │
                                                  │ generateToken(userId,    │
                                                  │   roles, expiry, secret) │
                                                  └────────────┬────────────┘
                                                               │
                                                               ▼
┌────────┐        200 OK { accessToken, refreshToken }        │
│ Client │ ◀──────────────────────────────────────────────────┘
└────────┘
```

**Flow B — Authorization (Subsequent Requests): Validating a token**

```
┌────────┐   GET /api/orders                       ┌──────────────────────────┐
│ Client │ ──Header: Authorization: Bearer <JWT>───▶│  JwtAuthenticationFilter  │
└────────┘                                          │  (OncePerRequestFilter)   │
                                                     └──────────┬────────────────┘
                                                                │
                                                    extract token from header
                                                                │
                                                                ▼
                                                     ┌──────────────────────┐
                                                     │      JwtUtils         │
                                                     │  validateToken()      │
                                                     │  - signature check    │
                                                     │  - expiry check       │
                                                     └──────────┬────────────┘
                                                                │
                                            ┌───────────────────┴───────────────────┐
                                            │                                       │
                                       ❌ invalid/expired                     ✅ valid
                                            │                                       │
                                            ▼                                       ▼
                                ┌──────────────────────┐              ┌──────────────────────────┐
                                │ AuthenticationEntry   │              │ Load UserDetails (from    │
                                │ Point → 401 response  │              │ claims or DB lookup)      │
                                └──────────────────────┘              └──────────┬────────────────┘
                                                                                  │
                                                                                  ▼
                                                                    ┌──────────────────────────┐
                                                                    │  SecurityContextHolder    │
                                                                    │  .setAuthentication(...)  │
                                                                    └──────────┬────────────────┘
                                                                                │
                                                                                ▼
                                                                    ┌──────────────────────────┐
                                                                    │  SecurityFilterChain      │
                                                                    │  → authorizeHttpRequests  │
                                                                    │  checks role/permission   │
                                                                    └──────────┬────────────────┘
                                                                                │
                                                                    ✅ authorized  │  ❌ forbidden
                                                                                │        │
                                                                                ▼        ▼
                                                                        Controller   AccessDeniedHandler
                                                                        executes     → 403 response
```

### 1.3 Key Principle

> The `JwtAuthenticationFilter` runs **once per request**, *before* Spring Security's authorization logic. It doesn't decide "is this user allowed to access this endpoint" — it only answers "who is this user, based on the token they presented." The `SecurityFilterChain`'s `authorizeHttpRequests` rules make the actual allow/deny decision afterward.

---

## 2. Architecture & Package Structure

### 2.1 Recommended Package Layout

```
com.example.app
│
├── config
│   ├── SecurityConfig.java          # SecurityFilterChain, AuthenticationManager, PasswordEncoder beans
│   ├── CorsConfig.java               # CORS configuration (or merged into SecurityConfig)
│
├── security
│   ├── jwt
│   │   ├── JwtUtils.java             # token generation, parsing, validation
│   │   ├── JwtAuthenticationFilter.java
│   │   └── JwtProperties.java        # @ConfigurationProperties for secret/expiry
│   ├── userdetails
│   │   ├── CustomUserDetails.java    # implements UserDetails, wraps your User entity
│   │   └── CustomUserDetailsService.java  # implements UserDetailsService
│   ├── handler
│   │   ├── CustomAuthenticationEntryPoint.java  # 401 handler
│   │   └── CustomAccessDeniedHandler.java       # 403 handler
│
├── controller
│   ├── AuthController.java           # /register, /login, /refresh, /logout
│   └── UserController.java           # example protected resource
│
├── service
│   ├── AuthService.java
│   └── RefreshTokenService.java      # rotation & revocation logic
│
├── repository
│   ├── UserRepository.java
│   └── RefreshTokenRepository.java
│
├── entity
│   ├── User.java
│   ├── Role.java
│   └── RefreshToken.java
│
├── dto
│   ├── request
│   │   ├── LoginRequest.java
│   │   ├── RegisterRequest.java
│   │   └── RefreshTokenRequest.java
│   └── response
│       ├── JwtAuthResponse.java
│       └── ApiErrorResponse.java
│
├── exception
│   ├── GlobalExceptionHandler.java   # @ControllerAdvice
│   ├── TokenRefreshException.java
│   └── UserAlreadyExistsException.java
│
└── AppApplication.java
```

### 2.2 Role of Each Core Component

| Component | Responsibility |
|---|---|
| **`SecurityFilterChain`** | The declarative security policy — which endpoints are public, which need auth, session policy, CSRF/CORS, and where custom filters are inserted into the chain. |
| **`AuthenticationManager`** | The orchestrator of the login attempt. Delegates to one or more `AuthenticationProvider`s to actually verify credentials. You typically don't write a custom one — you expose Spring's default via a bean. |
| **`AuthenticationProvider`** (`DaoAuthenticationProvider`) | Fetches the user via `UserDetailsService`, then compares the submitted password against the stored hash using `PasswordEncoder`. |
| **`UserDetailsService`** | Your bridge to the database. Given a username, returns a `UserDetails` object (or throws `UsernameNotFoundException`). This is the *only* place Spring Security talks to your persistence layer during authentication. |
| **`JwtAuthenticationFilter`** | Custom filter (not part of core Spring Security) that runs on *every* request, extracts the Bearer token, validates it, and — if valid — manually populates the `SecurityContextHolder` so downstream authorization logic sees an authenticated user. |
| **`SecurityContextHolder`** | Thread-local (or configurable) holder for the current `Authentication` object. Once set, `@PreAuthorize`, `hasRole()`, `Principal` injection, etc. all read from here. |
| **`AuthenticationEntryPoint`** | Invoked when an *unauthenticated* user hits a protected endpoint → returns 401. |
| **`AccessDeniedHandler`** | Invoked when an *authenticated* user lacks the required role/authority → returns 403. |

---

## 3. Step-by-Step Code Implementation

**Dependencies** (`pom.xml`):

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>

    <!-- JJWT (io.jsonwebtoken) 0.12.x API -->
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-api</artifactId>
        <version>0.12.6</version>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-impl</artifactId>
        <version>0.12.6</version>
        <scope>runtime</scope>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-jackson</artifactId>
        <version>0.12.6</version>
        <scope>runtime</scope>
    </dependency>
</dependencies>
```

### 3.0 Configuration Properties

`application.yml`:

```yaml
app:
  jwt:
    secret: ${JWT_SECRET}              # injected via env var — never hardcoded
    access-token-expiration-ms: 900000       # 15 minutes
    refresh-token-expiration-ms: 604800000   # 7 days
    issuer: my-application
```

```java
// security/jwt/JwtProperties.java
package com.example.app.security.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String secret,
        long accessTokenExpirationMs,
        long refreshTokenExpirationMs,
        String issuer
) {}
```

Enable it in your main class or a config class with `@EnableConfigurationProperties(JwtProperties.class)`.

---

### 3.1 `JwtUtils` — Token Creation, Parsing & Validation

```java
package com.example.app.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class JwtUtils {

    private final SecretKey signingKey;
    private final JwtProperties jwtProperties;

    public JwtUtils(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        // Secret must be a Base64-encoded string of sufficient length (>= 256 bits for HS256)
        this.signingKey = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes());
    }

    /** Generates a short-lived access token embedding username + roles. */
    public String generateAccessToken(UserDetails userDetails) {
        List<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtProperties.accessTokenExpirationMs());

        return Jwts.builder()
                .subject(userDetails.getUsername())
                .issuer(jwtProperties.issuer())
                .claim("roles", roles)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    /** Refresh tokens carry minimal claims — just enough to identify the user. */
    public String generateRefreshToken(UserDetails userDetails) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtProperties.refreshTokenExpirationMs());

        return Jwts.builder()
                .subject(userDetails.getUsername())
                .issuer(jwtProperties.issuer())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    @SuppressWarnings("unchecked")
    public List<String> extractRoles(String token) {
        return (List<String>) parseClaims(token).get("roles", List.class);
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            Claims claims = parseClaims(token);
            String username = claims.getSubject();
            return username.equals(userDetails.getUsername()) && !isExpired(claims);
        } catch (ExpiredJwtException | SignatureException | JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    /** Structural validation only — used before we even know the user (e.g. in the filter). */
    public boolean isTokenWellFormedAndUnexpired(String token) {
        try {
            Claims claims = parseClaims(token);
            return !isExpired(claims);
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    private boolean isExpired(Claims claims) {
        return claims.getExpiration().before(new Date());
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
```

**Key design decisions:**
- Roles are embedded as claims so the filter can build authorities **without a DB round-trip** on every request — a critical performance win at scale.
- `parseClaims` centralizes signature verification; any tampering throws `SignatureException` immediately.
- Access and refresh tokens use the same signing key here for simplicity; many production systems use a **separate signing key** for refresh tokens as extra defense-in-depth.

---

### 3.2 `JwtAuthenticationFilter`

```java
package com.example.app.security.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final UserDetailsService userDetailsService;

    private static final String HEADER_NAME = "Authorization";
    private static final String TOKEN_PREFIX = "Bearer ";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader(HEADER_NAME);

        if (authHeader == null || !authHeader.startsWith(TOKEN_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(TOKEN_PREFIX.length());

        try {
            final String username = jwtUtils.extractUsername(jwt);

            // Only populate context if not already authenticated in this request
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                if (jwtUtils.isTokenValid(jwt, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null, // credentials not needed post-authentication
                                    userDetails.getAuthorities()
                            );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (Exception ex) {
            // IMPORTANT: do not throw here. Let the request continue unauthenticated;
            // the SecurityFilterChain + AuthenticationEntryPoint will reject it with 401
            // if the endpoint required authentication. Swallowing here avoids leaking
            // stack traces and keeps this filter single-responsibility.
            log.debug("JWT validation failed: {}", ex.getMessage());
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    /** Skip the filter entirely for public auth endpoints to save a DB lookup. */
    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/api/auth/");
    }
}
```

---

### 3.3 `SecurityConfig`

```java
package com.example.app.config;

import com.example.app.security.handler.CustomAccessDeniedHandler;
import com.example.app.security.handler.CustomAuthenticationEntryPoint;
import com.example.app.security.jwt.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserDetailsService userDetailsService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomAuthenticationEntryPoint authenticationEntryPoint;
    private final CustomAccessDeniedHandler accessDeniedHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Stateless JWT APIs typically disable CSRF — there's no session/cookie to forge against
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/public/**").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )

            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(authenticationEntryPoint)  // 401
                .accessDeniedHandler(accessDeniedHandler)            // 403
            )

            // Insert our filter BEFORE Spring's default username/password filter
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12); // explicit strength factor
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("https://your-frontend.com"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
```

**Why `addFilterBefore(..., UsernamePasswordAuthenticationFilter.class)`?**
Spring Security's filter chain has a fixed conceptual ordering. We want our JWT check to happen early — before the framework's own credential-based filter — so that by the time authorization (`authorizeHttpRequests`) runs, `SecurityContextHolder` is already populated.

---

### 3.4 `AuthController`

```java
package com.example.app.controller;

import com.example.app.dto.request.LoginRequest;
import com.example.app.dto.request.RefreshTokenRequest;
import com.example.app.dto.request.RegisterRequest;
import com.example.app.dto.response.JwtAuthResponse;
import com.example.app.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/login")
    public ResponseEntity<JwtAuthResponse> login(@Valid @RequestBody LoginRequest request) {
        JwtAuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<JwtAuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        JwtAuthResponse response = authService.refreshToken(request.refreshToken());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.revokeRefreshToken(request.refreshToken());
        return ResponseEntity.noContent().build();
    }
}
```

Supporting `AuthService.login` sketch (ties the pieces together):

```java
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final JwtUtils jwtUtils;
    private final RefreshTokenService refreshTokenService;

    public JwtAuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.username(), request.password())
        ); // throws BadCredentialsException on failure -> handled globally

        UserDetails userDetails = userDetailsService.loadUserByUsername(request.username());

        String accessToken = jwtUtils.generateAccessToken(userDetails);
        String refreshToken = refreshTokenService.createRefreshToken(userDetails.getUsername());

        return new JwtAuthResponse(accessToken, refreshToken, "Bearer");
    }
}
```

---

## 4. Production-Grade Best Practices

### 4.1 Secret Key Management

- **Never** commit secrets to source control or hardcode them in `application.yml` committed to git.
- Inject via environment variable (`${JWT_SECRET}`) at minimum; for anything beyond a side project, use a proper secrets manager: **HashiCorp Vault**, **AWS Secrets Manager**, **Azure Key Vault**, or **GCP Secret Manager**, pulled in at startup via Spring Cloud Vault or the cloud provider's SDK.
- Use **HS256 with a key ≥ 256 bits** (32+ random bytes, Base64-encoded) — or better, move to **asymmetric signing (RS256/ES256)** so the public key can be distributed to resource servers/microservices for verification while only the auth server holds the private signing key. This matters a lot in a microservices topology where multiple services need to verify tokens but shouldn't be able to *mint* them.
- **Rotate keys periodically.** Support key rotation by embedding a `kid` (key ID) header in the JWT and maintaining a small registry of active verification keys, so you can rotate without invalidating every outstanding token instantly.

### 4.2 Refresh Tokens & Revocation

Access tokens should be short-lived (5–15 min) precisely *because* they can't be revoked cheaply. Refresh tokens bridge the UX gap while giving you a revocation point.

**Recommended architecture:**

1. Store refresh tokens **server-side** (DB table, or Redis for TTL-based auto-expiry) — as a hash, not plaintext, similar to how you'd store a password.
2. On `/refresh`, validate the token, look it up in the store, confirm it hasn't been revoked/used, then:
   - Issue a new access token.
   - **Rotate** the refresh token: invalidate the old one, issue a new one, store the new hash. This is called **refresh token rotation** and lets you detect theft — if an old, already-rotated token is ever replayed, treat it as a compromise signal and revoke the entire token family for that user.
3. On `/logout`, delete or mark the refresh token as revoked in the store.

**For access token revocation before natural expiry** (e.g., force-logout, compromised account), you have two real options:
- **Blacklist/deny-list** — maintain a fast-lookup store (Redis, with TTL matching the token's remaining lifetime) of revoked token IDs (`jti` claim). The filter checks this store on every request. This reintroduces a small stateful dependency, but it's a cache lookup, not a DB write, so it's cheap.
- **Short expiry + no blacklist** — accept that a revoked access token remains valid for up to its (short) TTL, and rely on the refresh token layer for anything beyond that window. Many production systems choose this to keep the request path fully stateless, accepting the bounded exposure window as a deliberate trade-off.

Either is legitimate; the choice depends on how sensitive your revocation SLA needs to be.

### 4.3 Global Exception Handling for Security Errors

**401 — `CustomAuthenticationEntryPoint`** (unauthenticated access to a protected resource):

```java
@Component
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public CustomAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                          AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        var body = new ApiErrorResponse(
                HttpStatus.UNAUTHORIZED.value(),
                "Unauthorized",
                "Authentication is required to access this resource",
                request.getRequestURI()
        );
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
```

**403 — `CustomAccessDeniedHandler`** (authenticated, but insufficient role/authority):

```java
@Component
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public CustomAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                        AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        var body = new ApiErrorResponse(
                HttpStatus.FORBIDDEN.value(),
                "Forbidden",
                "You do not have permission to access this resource",
                request.getRequestURI()
        );
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
```

**For everything else — business exceptions, validation errors — use `@ControllerAdvice`** (these run *inside* the DispatcherServlet, after Spring Security has already let the request through, so they can't handle 401/403 — that's precisely why the two handlers above exist separately):

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleBadCredentials(BadCredentialsException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiErrorResponse(401, "Invalid Credentials", ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ApiErrorResponse> handleUserExists(UserAlreadyExistsException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse(409, "Conflict", ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse(400, "Validation Failed", message, req.getRequestURI()));
    }
}
```

### 4.4 Additional Hardening Checklist

- **Always serve over HTTPS** — a JWT sent over plain HTTP is trivially interceptable.
- **Never store JWTs in `localStorage`** for browser clients if you can avoid it — it's vulnerable to XSS token theft. Prefer an **HttpOnly, Secure, SameSite=Strict cookie** for the refresh token, with the access token kept in memory (JS variable) only.
- **Validate the `alg` header** — never trust a client-supplied algorithm (guards against the classic "alg: none" or key-confusion attacks). JJWT's `parser().verifyWith(key)` API already pins this correctly as long as you don't manually parse unsigned claims first.
- **Include and check the `iss` (issuer) and `aud` (audience) claims** in multi-service environments so a token minted for Service A can't be replayed against Service B.
- **Rate-limit `/login` and `/refresh`** to blunt credential-stuffing and refresh-token brute forcing.
- **Log authentication failures** (without logging the token or password itself) for audit and anomaly detection.
