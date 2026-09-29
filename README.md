# packbase
Self-hosted web app for tracking and organizing your hiking gear inventory. Built with Spring Boot + Angular.

## Running locally

### Prerequisites

- Java 21
- Node.js + npm (the frontend uses npm 11)
- Docker (for the local PostgreSQL 16 database)

### 1. Start the database

From the repository root:

```sh
docker compose -f infra/docker-compose.yml up -d
```

The backend connects to `localhost:5432/packbase` as `packbase_app` / `localdev`. The schema is created by Flyway on first start.

### 2. Start the backend

From `backend/` (the Gradle wrapper lives there, not in the repository root):

```sh
cd backend
./gradlew bootRun      # Git Bash / Linux / macOS
.\gradlew.bat bootRun  # PowerShell / cmd (the `.\` is required in PowerShell)
```

The API is served on http://localhost:8080/api/v1. `bootRun` also starts the database if it isn't already running, so step 1 is optional when using Gradle.

Other useful tasks:

```sh
./gradlew dev               # build + all tests, then run with the "local" profile
./gradlew test              # unit tests
./gradlew integrationTest   # integration tests (need the database)
./gradlew dbDown            # stop the database container
```

### 3. Start the frontend

In a second terminal, from `frontend/`:

```sh
npm install
npm start
```

Open http://localhost:4200. The dev server proxies `/api` to the backend on port 8080, so start the backend first.

### 4. Use the app

Register an account on the login page (email + password), then create items in your library and build packing lists by dragging items into them.

### Troubleshooting

- **PowerShell: "running scripts is disabled on this system" when running `npm`:** either allow local scripts once with `Set-ExecutionPolicy -Scope CurrentUser -ExecutionPolicy RemoteSigned`, or use the `.cmd` shims instead: `npm.cmd install`, `npm.cmd start`.
- **Database errors after upgrading from an older checkout:** reset the database with `docker compose -f infra/docker-compose.yml down -v` and start it again.
- **Machine-specific config:** put overrides in `backend/src/main/resources/application-local.yml` (gitignored) and activate it with `./gradlew bootRunLocal`.
- **Frontend tests:** `npm test` in `frontend/`.
