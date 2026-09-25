# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Packbase is a self-hosted web app for tracking and organizing a hiking gear inventory. It is a monorepo in an early scaffolding stage:

- `api/openapi.yaml` — OpenAPI 3.0 contract (source of truth for the REST API)
- `backend/` — Spring Boot 4.1 / Java 21 / Gradle (Groovy DSL) service (`com.packbase.backend`)
- `frontend/` — Angular app (planned; directory currently empty)
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
- **Security:** `spring-boot-starter-security` is on the classpath with no custom config yet, so Spring Boot's default applies — every endpoint requires HTTP Basic auth with user `user` and a password generated and logged at startup. Expect 401s until a `SecurityFilterChain` is added.
- **Local config/secrets:** `application-local.yml`/`.properties` and `.env*` (except `.env.example`) are gitignored; put machine-specific overrides there. `docker-compose.override.yml` is also gitignored.
