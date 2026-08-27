# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository Overview

`framework4j` (Maven groupId `fun.commons`, version `1.4.2`) is a multi-module Spring Boot 3.5 / Java 17 enterprise SDK. Each module is an independently importable starter, and `framework4j-all` aggregates them. All modules publish their own Spring Boot auto-configuration through `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.

| Module | Purpose | Config prefix |
| --- | --- | --- |
| `framework4j-api` | Unified `ApiCode` enum (error code contract shared by all modules) | — |
| `framework4j-web` | `ApiResponse`/`ApiAssert`, `GlobalExceptionHandler` (all errors → HTTP 200 + business code), `TraceContext`, Jackson WebConfig (snake_case + Long→String) | `framework4j.web.enabled` + `framework4j.web.jackson.{snake-case,long-to-string,fail-on-unknown-properties}` |
| `framework4j-datetime` | `OffsetDateTime` serializers + `TimeFormatInterceptor` + converters (split from core in v2.0) | `framework4j.datetime.enabled` |
| `framework4j-id` | `SnowflakeDistributor` + `WorkerIdStrategy` (Redis-leased / IP-hash), `OpenID` obfuscation (12-char + checksum + TypeHandler + Swagger) (split from core in v2.0) | `framework4j.id.*`, `framework4j.openid.*` |
| `framework4j-sql-tracing` | `TraceIdDruidFilter` + `DefaultTraceIdProvider` + 3 modes (DISABLED/WRITE_ONLY/ALL) (split from datasource in v2.0) | `framework4j.datasource.sql-tracing.*` |
| `framework4j-redis` | Multi-Redis data source manager (`MultiRedisManager`), per-data-source Lettuce + Redisson, `@RedisOn("name")` annotation processor (class- or field-level), Jackson serializer | `framework4j.redis.*` |
| `framework4j-datasource` | Multi-DataSource on top of Druid + MyBatis-Plus, `@DataSourceOn("name")` annotation processor | `framework4j.datasource.*` |
| `framework4j-accesstoken` | JWT + Redis dual-verification token SDK, `TokenInterceptor` (router) + `AccessTokenValidationStrategy` / `RefreshTokenValidationStrategy`, `@RequiresToken` (with `type()` for access/refresh routing, `roles()`/`anyRole()` role authz since v1.4.1), `RoleAuthorizer`, `TokenContext`, `AccessTokenGenerator#updateClaims` (role changes take effect live), `RefreshTokenService` (family + Lua atomic rotation + poison pill + maxRotations), `TokenKeyBuilder` | `framework4j.access-token.*` (note the **kebab-case**, not camelCase) |
| `framework4j-idempotency` | `Idempotency-Key` + Redis 48h retention | `framework4j.idempotency.*` |
| `framework4j-signature` | HMAC-SHA256 request-signature anti-replay (4 headers + nonce) | `framework4j.signature.*` |
| `framework4j-rate-limit` | Distributed rate limiting (Lua sliding window + rate-limit response headers) | `framework4j.rate-limit.*` |
| `framework4j-cache` | Multi-level cache (Caffeine L1 + Redis L2 + single-flight stampede protection) | `framework4j.cache.*` |
| `framework4j-audit` | Audit log (`@Auditable` AOP + hash-chain tamper protection) | `framework4j.audit.*` |
| `framework4j-sensitive` | Field masking (Jackson) + AES-256-GCM encryption (MyBatis TypeHandler) | `framework4j.sensitive.*` |
| `framework4j-transport` | HTTP transport abstraction (RestTemplate / WebClient switch), shared by other modules. Since v1.4.2 (Issue #18) `framework4jHttpTransport` resolves its RestTemplate via `ObjectProvider` + optional `rest-template-bean-name` pin — ≥2 business RestTemplates no longer crash startup (falls back to built-in default + WARN); 0/1-bean reuse semantics unchanged | `framework4j.transport.*` |
| `framework4j-tracelog` | Runtime trace logging (logback appender + sampling/rate-limiting + query API + sensitive-field masking; added v1.3.0) | `framework4j.tracelog.*` |
| `framework4j-all` | Convenience aggregator pulling all 16 starters (api, web, datetime, id, sql-tracing, redis, datasource, accesstoken, idempotency, signature, rate-limit, cache, audit, sensitive, transport, tracelog) | — |

## Common commands

Build and test from the repository root (Java 17 required; the root `pom.xml` sets `-XX:+EnableDynamicAgentLoading -Djdk.attach.allowAttachSelf=true` in surefire so Lombok/Mockito work on JDK 17+).

```bash
# Full build (compiles all modules, runs all tests, attaches sources + javadocs)
mvn clean verify

# Compile only (skip tests)
mvn -DskipTests package

# Run all tests in the whole reactor
mvn test

# Run all tests in a single module
mvn -pl framework4j-api test
mvn -pl framework4j-redis test
mvn -pl framework4j-datasource test
mvn -pl framework4j-accesstoken test

# Run a single test class
mvn -pl framework4j-redis -Dtest=MultiRedisManagerTest test
mvn -pl framework4j-accesstoken -Dtest=BasicIntegrationTest test

# Run a single test method (Surefire method matching)
mvn -pl framework4j-api -Dtest=ApiCodeTest test

# Run by directory pattern (tests are grouped under unit/, functional/, performance/, integration/)
mvn -pl framework4j-datasource -Dtest='**/functional/*Test' test
mvn -pl framework4j-datasource -Dtest='**/unit/*Test' test

# Install to local Maven (~/.m2) so other projects can depend on the SNAPSHOT
mvn -DskipTests install
mvn install    # also runs tests
```

### Prerequisites for tests

- **Redis on `localhost:6379`** — required by `framework4j-redis`, `framework4j-accesstoken`, and parts of `framework4j-id` (Redis-leased `WorkerIdStrategy`).
- **`framework4j-accesstoken` performance tests** spin up an in-process Redis via `it.ozimov:embedded-redis` (already on the test classpath).
- **`framework4j-datasource`** has both H2 (default) and PostgreSQL profiles; only the PostgreSQL profile (`application-pgsql-test.yml`) needs an external Postgres instance.

If Redis is unavailable, prefer `mvn -pl framework4j-api test` — `application-test.yml` for that module uses IP-hash `WorkerIdStrategy` (now in `framework4j-id`) so it does not touch Redis.

## Architecture notes

### Auto-configuration pattern

Every module registers its configuration class via `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. Each starter is gated by an `framework4j.<feature>.enabled` property (default `true` for core, **opt-in `true`** for redis/datasource/access-token). To disable a module without removing the dependency, set the property to `false`.

### `@RedisOn` / `@DataSourceOn` runtime injection

Both modules use a `BeanPostProcessor` (registered inside their `MultiXAutoConfiguration`) that:

1. Scans classes/fields for the annotation.
2. Calls `MultiXManager#getXxx(name)` to fetch the named template / datasource.
3. Rewrites injection by replacing the bean with the per-name instance, supporting both field- and class-level placement and a `strict` flag (`true` throws if the named source is missing; `false` falls back to `default`).

If you add new fields that should follow this convention, mark them with the annotation rather than `@Qualifier`.

### Unified API response (framework4j-api)

`ApiResponse<T>` is the standard envelope (`code/message/data/error/trace_id`). `GlobalExceptionHandler` translates all `Exception`s (validation, type mismatch, duplicate key, `BadSqlGrammarException`, `NoHandlerFoundException`, etc.) into `ApiResponse.fail(...)` and **always returns HTTP 200**. `trace_id` is pulled from the Micrometer `Tracer` via `TraceContext.getTraceId()`. New `Exception`s thrown from controllers do not need explicit handling — but if you want a custom `ApiCode`, add it to the `ApiCode` enum (format: `ABCCC`, see `.claude/skills/mc-api-spec/API 响应结构与错误码规范 v1.6.md` §7).

### ID & OpenID (framework4j-id)

`SnowflakeDistributor` (Hutool Snowflake) is initialized from a `WorkerIdStrategy` (`RedisWorkerIdStrategy` lease-based, default; `IpHashWorkerIdStrategy` as fallback). The epoch is hardcoded to `2024-01-01 UTC+8`. The MyBatis-Plus `IdentifierGenerator` is registered automatically when `framework4j.id.mybatis.enabled=true` (default).

`OpenID` (in `framework4j-id/.../openid/`) uses `IdObfuscator` to convert `Long` ↔ 12-char strings (11 data + 1 checksum). End-to-end conversion is done via `OpenIdTypeHandler` (MyBatis), `@OpenId` field annotations, Jackson `OpenIdJsonSerializer` (applied via `OpenIdBeanSerializerModifier`), and Swagger customizers (`OpenIdSwaggerConfig` / `OpenIdSwaggerModelConfig`).

> **v2.2 toggle correctness**: `@OpenId` itself no longer carries `@JsonSerialize` (the old static-field annotation was bypassing Spring's conditional config, so `framework4j.openid.enabled=false` did not actually disable serialization). The serializer is now applied by `OpenIdAutoConfiguration` via `OpenIdBeanSerializerModifier` — so the toggle works. When disabled, `@OpenId Long` fields serialize as plain Long (subject to framework4j-web's Long→String if enabled).

> **v2.2 PathVariable fix (GitHub Issue #1)**: `@OpenId @PathVariable Long id` previously required the `-parameters` javac flag and silently failed without it (mapped to `10106 BUSINESS_RULE_ERROR`). Now `OpenIdAutoConfiguration` registers `OpenIdPathVariableArgumentResolver` via a `BeanPostProcessor` on `RequestMappingHandlerAdapter` (prepended, beats built-in `PathVariableMethodArgumentResolver`). The resolver reads `HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE` directly, bypassing `MethodParameter.getParameterName()` reflection. `OpenIdFailFastValidator` (startup listener, `framework4j.openid.fail-fast=true` default) fails startup if any `@OpenId @PathVariable` can't resolve its name.

> **v1.3 request-body deserialization + registration fix (06 requirements R1/R2/R3)**: `@RequestBody` JSON now round-trips `@OpenId` — `OpenIdBeanDeserializerModifier` (same per-bean recursion as the serializer) decodes scalar `Long`, `List<Long>`/`Set<Long>`/`Long[]`/`long[]`, and nested record/object fields for free (no `@OpenIdRecursive` needed). The serializer modifier was also made type-aware so `@OpenId List<Long>` emits an obfuscated string array (previously broke under Jackson — its only test was `@Disabled`). `Integer`/`Map` are intentionally not handled on the body side (R5 = option A). Sub-toggle `framework4j.openid.request-body-deserializer` (default `true`). **Critical**: the whole OpenId Jackson module is registered via a `BeanPostProcessor` that calls `mapper.registerModule(...)` directly — **not** via `Jackson2ObjectMapperBuilderCustomizer.modulesToInstall`. Two `modulesToInstall` customizers in the same app (OpenId + framework4j-web's Long→String) orphan the earlier module (`setupModule` never runs, so nothing obfuscates/decodes). `OpenIdRequestBodyIntegrationTest` locks the container-level wiring.

> **v1.3 三开关 + Long 枢轴 (06 requirements R4/R5/R6)**: all `@OpenId` value handling pivots through `Long` via `OpenIdLongCodec` (`openid.util`; decode text/token → Long pivot → target scalar; encode Number/numeric-String → obfuscated). Type eligibility is centralized in `OpenIdTypeSupport` (read from Environment). Three global toggles under `framework4j.openid.*`: `support-integer` (default `false`, opt-in `Integer/int` three-channel — note: the path/query `OpenIdFormatterFactory` previously supported Integer always; now aligned to the switch), `support-string` (default `false`, opt-in `String`/`List<String>` etc. — the lightest R4 migration: keep `@PathVariable String id`, just add `@OpenId`; the String field holds the decoded numeric string in memory, obfuscated only on the wire, strict), `accept-numeric-fallback` (default `true`; `false` rejects bare numbers everywhere to force obfuscation post-migration). `Formatter` + `OpenIdPathVariableArgumentResolver` use the codec/type-support. **Legacy `OpenIdTypeUtils` retired and deleted** (its container-handling behavior lives in the Jackson serializer/deserializer classes; its tests were removed with it). `OpenIdFailFastValidator` now also scans `@RequestBody` DTO graphs (recursive, cycle-guarded) for `@OpenId` on unsupported types → loud startup failure, and uses FQN in messages. R7 `strict` was skipped (numeric passthrough already protects real Long fields).

When changing the obfuscator, run the regression suite under `framework4j-id/src/test/java/fun/commons/framework4j/id/` and `.../openid/`.

### AccessToken

`AccessTokenAutoConfiguration` registers `AccessTokenGenerator` (JWT issuance + Redis state for access tokens) and `RefreshTokenService` (refresh token family + Lua atomic rotation + poison pill + maxRotations). `TokenInterceptor` (registered in `AccessTokenWebMvcConfig`) routes by `@RequiresToken.type()` to `AccessTokenValidationStrategy` (default) or `RefreshTokenValidationStrategy`. Both pull their `StringRedisTemplate` via `MultiRedisManager.getStringRedisTemplate(redisName)` — the module **requires** `framework4j-redis` even for a single Redis. Policies (login/reset/invite/...) are configured under `framework4j.access-token.policies.<TOKEN_TYPE>`. The Redis key prefix uses `spring.application.name` (mandatory — missing `spring.application.name` will fail startup). Interceptor path patterns are configurable via `framework4j.access-token.path-patterns` / `exclude-path-patterns`.

> **v1.2.8 registration fix (downstream benefit4j "claims → TokenContext" report — misdiagnosed)**: `AccessTokenAutoConfiguration` now `@Import`s `AccessTokenWebMvcConfig` — same orphan-class pattern as idempotency v1.2.5: the registration class had `@Configuration` but was loaded nowhere, so `TokenInterceptor` never entered the MVC chain, `@RequiresToken` was never enforced, and `TokenContext` was never populated (**every** `getClaim` returned null). The claims → Redis → TokenContext chain itself was always healthy (proven by `WebIntegrationTest`, which registered the interceptor itself). Consumers must not register `TokenInterceptor` themselves. Also fixed a latent renew bug the double-registration exposed: `renewIncrement != null` was always true (`asLong` returns boxed 0, never null), so `autoRenew` policies without `renew-increment` ran `expire(key, 0)` — **deleting the token metadata on first use** (second request → 10201). Condition is now `renewIncrement > 0`.

> **v1.4.1 role authz + empty path-patterns semantics (Issues #16/#17, Plan A only)**: `@RequiresToken` grew `roles()` (ALL must match) / `anyRole()` (ANY matches; both declared → both must hold), empty defaults = no check, so legacy annotations are unaffected. The check runs in `RoleAuthorizer` — a post-auth hook inside `TokenInterceptor` invoked **after** access validation succeeds (`AccessTokenValidationStrategy` has already populated `TokenContext`); `type=refresh` endpoints skip it. Roles are read from **Redis claims** key `"roles"` (`RoleAuthorizer.CLAIM_KEY_ROLES`) — NOT the JWT payload — which is why `AccessTokenGenerator#updateClaims(tokenType, uid, claims)` (whole-map replace, TTL preserved via `SET KEEPTTL`, Redis 6.0+; `false` if user has no live token; uid-key policies only, same convention as `revokeByUser`) makes role changes effective on the **next request across all devices** — no re-issuance, no re-login. Fail-closed: annotation declares roles but the token lacks the claim → `10300 FORBIDDEN` (distinct from 10200 unauthenticated) — existing tokens 403 on newly protected endpoints until re-login or `updateClaims`. Plan B (`@RequiresPermission` + `PermissionChecker` SPI, runtime-mutable permission keys) was deliberately deferred; `RoleAuthorizer` is the extension point it would bolt onto. Behavior change: an explicitly **empty** `framework4j.access-token.path-patterns` now **skips interceptor registration** with a WARN (Spring's `addPathPatterns(empty)` used to mean intercept `/**`); default `/**` and non-empty lists unchanged.

### Idempotency (framework4j-idempotency)

`IdempotencyInterceptor` protects write methods only (POST/PUT/PATCH/DELETE) on `framework4j.idempotency.path-patterns` (default `/api/**`). Flow: UUID v4 validation (400 otherwise) → SHA-256 body hash → **atomic Lua GET+SETNX** of `{keyPrefix}:{requestURI}:{uuid}` = `{hash}|PENDING` (TTL `ttl-seconds`, default 48h). Existing value semantics: hash mismatch → 409 `10501 DUPLICATE_SUBMIT`; `OK:{body}` → replay cached response (HTTP 200); `PENDING` → 409 "前一请求仍在处理中，请稍后重试". `afterCompletion`: 2xx writes back `{hash}|OK:{body}`; non-2xx/exception **deletes the key** (client may retry). Redis check failure is **fail-closed** (exception propagates).

> **v1.2.5 registration fix (downstream benefit4j report #5)**: `IdempotencyAutoConfiguration` now `@Import`s `IdempotencyWebMvcConfig` — previously the registration class existed but was wired nowhere (v2.1 @Component→@Bean migration orphaned it), so the interceptor bean was created but never entered the MVC chain and idempotency silently did nothing. Consumers must **not** register `IdempotencyInterceptor` themselves.
>
> **v1.2.7 reentrancy guard (report bug2 "first request always 409")**: `preHandle` now passes through if the request already carries the SETNX-success attribute — defends against the interceptor being registered twice (framework + consumer workaround), where the second pass read its own freshly-written PENDING and 409'd the same request. If "first request 409" reproduces after removing duplicate registration, check for **reused fixed UUIDs** (48h TTL residue from earlier runs).

### SQL tracing (framework4j-sql-tracing)

`SqlTracingAutoConfiguration` (split from `framework4j-datasource` in v2.0) registers `TraceIdDruidFilter`, which prefixes every SQL with the current trace ID. The provider (`DefaultTraceIdProvider`) reads from SLF4J MDC (Micrometer Tracing populates it); override the `TraceIdProvider` bean to source trace IDs from somewhere else (e.g. a custom header). Modes: `DISABLED` / `WRITE_ONLY` (INSERT/UPDATE/DELETE) / `ALL` (default).

> **v1.2.5 config lookup precedence (downstream benefit4j report #6 fix)**: `SqlTracingBeanPostProcessor` binds `SqlTracingProperties` per Druid datasource with **per-datasource first, global fallback**: `framework4j.datasource.datasources.{name}.sql-tracing.*` wins if present (override), else `framework4j.datasource.sql-tracing.*` (same prefix as the master switch — the documented location now actually works). Before v1.2.5 only the per-datasource prefix was checked, so apps that configured only the global prefix had the master switch on but **no filter injected, silently**. Skips are now logged loudly (WARN when no config found at either level, INFO when `mode=DISABLED`); bind exceptions are no longer swallowed.

## Conventions

- **Package root**: `fun.commons.framework4j.<module>` (note: `framework4j-accesstoken` actually puts classes under `fun.commons.framework4j.accesstoken`, matching the root — be aware of this when grepping).
- **Configuration properties** are `@ConfigurationProperties` classes named `XxxProperties`, prefixed with `framework4j.*`. Bind them via `@EnableConfigurationProperties(...)` in the `AutoConfiguration` class.
- **Lombok** (`@Slf4j`, `@Data`, `@Builder`, etc.) is the norm; do not introduce plain getters/setters.
- **JSON** strategy: **all modules use Jackson** (Spring Boot default, snake_case + Long→String via `Jackson2ObjectMapperBuilderCustomizer`); fastjson2 was fully removed in v2.0 (autotype RCE risk eliminated). `framework4j-redis` uses `JsonRedisSerializer` based on Jackson with `activateDefaultTyping` + `BasicPolymorphicTypeValidator`. See `mc-java-spec` §1.4 + SDK whitelist v1.3 §3.1.
- **Tests** follow JUnit 5 (`@Test`, `@DisplayName`) and are grouped by `unit/`, `functional/`, `integration/`, `performance/`. Spring Boot tests load `application-test.yml` from `src/test/resources/`. Mockito 5 + ByteBuddy are pre-configured in the parent POM.
- **Logging** uses SLF4J via Lombok's `@Slf4j`. New beans log initialization with a stable Chinese tag prefix in square brackets (e.g. `【Multi-Redis】`, `【ID-SDK】`, `【AccessToken】`) — keep this style for log consistency.
- **Module status** lives only on the working tree — `target/` is gitignored (see `.gitignore`); rebuild rather than reusing leftover artifacts.

## Reference documentation in the repo

Per-module design docs (Chinese) live next to the source:

- `framework4j-api/API 响应结构与错误码规范 v1.3.md`, `API 统一响应与错误码 SDK (v1.3) 使用指南.md` — module-level error-code conventions (authoritative spec is `.claude/skills/mc-api-spec/API 响应结构与错误码规范 v1.6.md`)
- `framework4j-id/OpenID 模块使用指南.md`, `OpenID 快速开始.md`, `分布式 ID 安全混淆 (OpenID) 技术方案.md` — OpenID/obfuscator design (moved from core to id in v2.0)
- `framework4j-datetime/Java开发时间处理方案.md`, `Java开发时间处理规范.md` — datetime module (moved from core in v2.0)
- `framework4j-id/framework4j 分布式 ID SDK 产品文档.md`, `framework4j 分布式 ID SDK 使用指南.md` — Snowflake + WorkerId (moved from core in v2.0)
- `framework4j-redis/多Redis数据源注入器产品文档.md`, `RedisOn注解设计文档.md`
- `framework4j-datasource/多Datasource数据源注入器产品文档v2.md`, `Spring Boot 3 + Micrometer 全链路 SQL 追踪方案.md`, `SQL追踪TraceID疑难解答.md`, `README-TESTS.md`
- `framework4j-accesstoken/AccessToken SDK 测试文档.md`, `framework4j-accesstoken 用户指南.md`, `framework4j-accesstoken快速开始.md`

These are the source of truth for feature behavior — prefer reading them over grepping code when reasoning about a module.