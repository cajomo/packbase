# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Packbase is a self-hosted web app for tracking and organizing a hiking gear inventory. It is a monorepo in an early scaffolding stage:

- `api/openapi.yaml` — OpenAPI 3.0 contract (source of truth for the REST API)
- `backend/` — Spring Boot 4.1 / Java 21 / Gradle (Groovy DSL) service (`com.packbase.backend`)
- `frontend/` — Angular app (login, lists overview, list editor with drag-and-drop from a per-user item library). Currently **mocked**: users, lists and items live in localStorage via `PackStore` (`store.service.ts`) and do not call the backend yet. `npm start` serves on :4200; `proxy.conf.json` proxies `/api` to `localhost:8080` for when it does.
- `infra/docker-compose.yml` — local PostgreSQL 16
- `.github/workflows/` — CI (currently empty)

## Commands

Start the database first (the backend connects to `localhost:5432/packbase` as `packbase_app` / `localdev`):

```sh
docker compose -f infra/docker-compose.yml up -d
```

Backend (run from `backend/`; use `gradlew.bat` on Windows cmd/PowerShell, `./gradlew` in Git Bash):

```sh
./gradlew dev                                    # build + all tests, start db, run app with "local" profile
./gradlew bootRun                                # run on :8080 (starts db first)
./gradlew bootRunLocal                           # same, with spring.profiles.active=local
./gradlew dbUp / dbDown                          # start (waits for healthy) / stop the Postgres container
./gradlew test                                   # unit tests only (excludes @Tag("integration"))
./gradlew integrationTest                        # @Tag("integration") tests; starts db first
./gradlew test --tests ClassName                 # single test class
./gradlew test --tests "ClassName.methodName"    # single test method
./gradlew build                                  # build + test + integrationTest; jar in build/libs/
```

`@SpringBootTest` tests load the full context and need Postgres — tag them `@Tag("integration")` so they run in `integrationTest`, not `test`.

`openApiGenerate` (runs before `compileJava`) generates the `GearApi` interface (`com.packbase.backend.api`) and DTOs (`com.packbase.backend.api.model`) from `api/openapi.yaml` into `build/generated/openapi`. Controllers implement the generated interface; don't hand-write DTOs that the spec defines.

## Architecture notes

- **API-contract first.** Endpoints are defined in `api/openapi.yaml` before implementation. The server base path is `/api/v1`. Controllers and DTOs should match the spec's `operationId`s and schemas (`GearItemInput` for request bodies, `GearItem` = input + `id` (UUID) + `createdAt` for responses; error bodies are `ErrorResponse` = `{ "message": string }`). Update the spec when changing the API.
- **Persistence:** Spring Data JPA against PostgreSQL. Schema is currently managed by Hibernate `ddl-auto: update` (no migration tool yet); `show-sql` is on.
- **Security:** `config/SecurityConfig` defines a `SecurityFilterChain`. It is a **temporary** setup: CSRF is disabled (stateless JSON API), `/api/v1/**` is `permitAll()` (the API is open, no real auth yet), and any other path requires HTTP Basic auth. Spring Boot's default user is `user`; the password is generated and logged at startup unless overridden (the `local` profile sets `spring.security.user.password: localdev` in `application-local.yml`). Replace the `permitAll()` rule when real authentication is added. `@WebMvcTest` slices must `@Import(SecurityConfig.class)` (as `GearControllerTest` does), otherwise Spring's default chain applies and requests get 401/403.
- **Local config/secrets:** `application-local.yml`/`.properties` and `.env*` (except `.env.example`) are gitignored; put machine-specific overrides there. `docker-compose.override.yml` is also gitignored.
