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
- **Persistence:** Spring Data JPA against PostgreSQL. The schema is managed by **Flyway** migrations in `backend/src/main/resources/db/migration` (`V<n>__description.sql`); Hibernate runs with `ddl-auto: validate`, so an entity change needs a new migration. Never edit an applied migration. `show-sql` is on. If your local DB predates Flyway (created by the old `ddl-auto: update`), reset it with `docker compose -f infra/docker-compose.yml down -v`.
- **Auth (session cookie):** `auth/` package. Users log in with **email + password** (`app_user` table, email stored trimmed and lower-cased, passwords hashed with Argon2id as `{argon2}...`). Endpoints `/auth/register`, `/auth/login`, `/auth/logout`, `/auth/me` are in the OpenAPI spec; login is a hand-written controller that authenticates via `AuthenticationManager`, rotates the session id (anti fixation) and stores the context in the HTTP session. Login errors are deliberately generic ("Invalid email or password").
- **Security:** `config/SecurityConfig`. Everything under `/api/v1` requires a session except `POST /auth/register|login|logout`; unauthenticated requests get a plain `401` (no Basic prompt, no redirect). CSRF is **on** using `CookieCsrfTokenRepository`: the readable `XSRF-TOKEN` cookie must be echoed in the `X-XSRF-TOKEN` header on POST/PUT/DELETE (Angular's `HttpClient` does this automatically; any request, e.g. `GET /auth/me`, sets the cookie). Session cookie is `HttpOnly; Secure; SameSite=Lax` (`application.yaml`; Chrome/Firefox accept `Secure` on `http://localhost`). The request cache is disabled so anonymous requests never create sessions. Sessions are in-memory (lost on restart, not shared between instances).
- **Ownership:** every `gear_item` has an `owner_id`; `GearService` methods take the owner id (from `CurrentUser.id()` in the controller) and another user's item looks like a 404. New per-user resources must follow the same pattern.
- **Tests and security:** `@WebMvcTest` slices must `@Import(SecurityConfig.class)` (as `GearControllerTest` does) and send `.with(user(new PackbaseUser(...)))` plus `.with(csrf())` on writes. Integration tests use `AuthTestSupport.registerAndLogin(mvc)` to get an authenticated `MockHttpSession`. MockMvc has no servlet container, so cookie flags (`Secure`, `SameSite`) cannot be asserted there; check them with curl against a running server.
- **Local config/secrets:** `application-local.yml`/`.properties` and `.env*` (except `.env.example`) are gitignored; put machine-specific overrides there. `docker-compose.override.yml` is also gitignored.
