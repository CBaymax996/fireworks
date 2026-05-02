# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Fireworks is a self-hosted password vault. Two-module Gradle build:

- `fireworks-server` — Spring Boot 4.0.6 + Kotlin 2.3.20 backend (Java 25 toolchain). SQLite via Ktorm. Group: `site.hanabii`, root package: `site.hanabii.fireworks`.
- `fireworks-web` — Vue 3 + Vite + TypeScript frontend (Pinia, vue-router). Built artifacts are embedded into the Spring Boot jar's `static/` for production.

`settings.gradle.kts` registers both modules; `build.gradle.kts` declares the Spring Boot / Kotlin plugins (`apply false`) and an Aliyun maven mirror for China network conditions.

## Common Commands

Run from the repository root unless noted.

### Run the application

```sh
# Production-like: builds frontend (npm run build-only) and serves dist from Spring Boot
./gradlew :fireworks-server:bootRun

# Dev mode: starts Spring Boot AND the Vue dev server (HMR) in parallel.
# `processResources` skips the frontend build and static copy when any task name contains "Dev".
# The Vite dev server proxies /api -> http://localhost:8080.
./gradlew :fireworks-server:bootRunDev
```

App listens on `:8080`. SQLite file: `./fireworks-test.db` (gitignored). Schema is auto-created on `ApplicationReadyEvent` from the `DDL` constants on each `*DO` object — disable via `fireworks.db.init-schema=false`.

### Backend build/test

```sh
./gradlew :fireworks-server:build              # full build incl. tests
./gradlew :fireworks-server:test               # all unit tests (JUnit 5 + mockito-kotlin)
./gradlew :fireworks-server:test --tests "site.hanabii.fireworks.app.VaultServiceTest"
./gradlew :fireworks-server:test --tests "*VaultControllerTest.login*"
```

### Frontend (run inside `fireworks-web/`)

```sh
npm install
npm run dev          # Vite dev server, proxies /api -> :8080
npm run build        # type-check + production build into dist/
npm run build-only   # build skipping vue-tsc (used by Gradle)
npm run type-check   # vue-tsc --build
npm run test:unit    # vitest
npm run test:e2e     # playwright (run `npx playwright install` once first)
npm run lint         # runs oxlint --fix then eslint --fix --cache
npm run format       # prettier
```

A single Playwright spec: `npm run test:e2e -- tests/example.spec.ts --project=chromium`.

The Gradle wrapper task `:fireworks-web:buildFrontend` invokes `npm run build-only` with declared inputs/outputs so it skips when sources are unchanged.

## Backend Architecture

The server follows a strict three-layer split rooted at `site.hanabii.fireworks`:

- `domain/` — Pure Kotlin data classes and repository **interfaces** (`Account`, `User`, `PasswordEntry`, `VaultConfig`, `*Repository`). No framework imports.
- `app/` — Spring `@RestController` and `@Service` classes; orchestrates use cases. Crypto lives at `app/crypto/`.
- `infra/` — Ktorm `Table` definitions (`*DO.kt`, each carries a `const val DDL` and a `QueryRowSet.toX()` extension) and `*RepositoryImpl` classes that implement the domain interfaces. Spring config lives at `infra/config/` (`KtormConfig`, `SchemaInitializer`).

Domain interfaces are wired to infra implementations purely via Spring component scanning — there is no separate config module. When adding a new entity, follow the existing pattern: define `Foo` in `domain/`, `FooRepository` interface in `domain/`, `FooDO` table object with a `DDL` constant in `infra/`, and `FooRepositoryImpl` in `infra/`. Then register the DDL in `SchemaInitializer.init()`.

### Two distinct auth flows

This is easy to confuse. Both store state in the same `HttpSession` but under different attributes.

1. **Web account auth** (`/api/auth/*`): username/password registration and login for the application itself. Passwords are BCrypt-hashed in the `accounts` table. `AuthSessionService` stores `accountId` on the session.
2. **Vault unlock** (`/api/vault/*`): a separate master password that gates the encrypted password entries. `VaultService.setup` stores BCrypt(masterPassword) + a random salt in `vault_config`. On `/vault/login`, `VaultSessionService` derives the AES-256 key via PBKDF2 (100,000 iters, HMAC-SHA256) and stashes the raw key bytes in the session under `vaultKey`. `VaultController.requireKey()` reconstructs the `SecretKey` on each request.

`PasswordEntry.password` is plaintext **in memory only** — controllers receive plaintext, `VaultService` encrypts before persistence (AES-GCM, 12-byte IV, 128-bit tag, Base64-encoded `IV || ciphertext || authTag`), and decrypts before returning. Never persist plaintext through the entry repository.

### Error handling

All API errors flow through one mechanism (`UserController.kt`, despite the filename, holds the shared bits):

- `AppException(code: ErrorCode, status: HttpStatus, message: String)` — throw this from controllers/services.
- `ErrorCode` enum — extend it when adding new error categories; the enum name becomes the wire `code`.
- `@RestControllerAdvice GlobalExceptionHandler` — maps `AppException` to the structured `ErrorResponse(code, message, path, timestamp)`. Also catches `HttpMessageNotReadableException`, `MethodArgumentTypeMismatchException`, `IllegalArgumentException` → 400, and any other `Exception` → 500 with a generic message (don't leak internals).

Don't add ad-hoc `ResponseEntity.badRequest()` calls in controllers; throw `AppException` so the response shape stays consistent.

### OpenAPI

`springdoc-openapi-starter-webmvc-ui` is on the classpath. Annotate new endpoints with `@Operation` / `@ApiResponses` (see `AuthController` for the established pattern). Swagger UI is at `/swagger-ui.html` when the app is running.

## Frontend Architecture

`fireworks-web/src/` layout:

- `api/` — thin `fetch` wrappers that hit the backend. The shared helper sets `credentials: 'include'` so the Spring `JSESSIONID` cookie flows on every call. Errors are turned into `Error(body.message || 'HTTP <status>')`.
- `stores/` — Pinia stores that wrap the api modules and expose reactive `loading` / `error` / domain state (e.g. `useAuthStore` exposes `account`, `isAuthenticated`, `register`, `login`, `logout`, `checkStatus`).
- `views/` — top-level routed pages.
- `router/index.ts` — `createWebHistory` with code-split lazy routes for non-home pages.
- `components/` — currently the Vite scaffolded examples; replace as features land.
- `@` alias resolves to `src/`.

In dev, `vite.config.ts` proxies `/api` → `http://localhost:8080` so frontend and backend can run on different ports while sharing the session cookie. In production, the Vue app is served by Spring Boot from the same origin so cookies just work.

## Conventions worth knowing

- Code comments and commit messages are written in Chinese; keep the convention when editing or adding code in this repo.
- `BCryptPasswordEncoder` is instantiated as a private field (not a Spring bean) in both `AuthService` and `VaultService` — preserve that pattern unless deliberately refactoring.
- `*DO` files own both the Ktorm table mapping and the `DDL` schema string. The DDL must use `IF NOT EXISTS` because `SchemaInitializer` runs every startup.
- Master password supports Unicode (e.g. Chinese) — `VaultCryptoService.deriveKey` uses `String.toCharArray()` so PBKDF2 sees code points, not pinyin or transliteration.
- The vault key is stored as **raw bytes** in the session (`SecretKey.encoded`), then rehydrated with `SecretKeySpec(bytes, "AES")` on read. Sessions are in-memory; restarting the server forces every user to re-unlock.
- `*.db` files are gitignored. Don't commit local SQLite databases or test fixtures that produce them.
