# Lost Then Found Backend

Spring Boot backend for the Lost Then Found Vite/React frontend.

## Stack

- Java 21
- Spring Boot 4.1.0
- Maven
- Spring Web MVC
- Spring Data JPA
- Validation
- H2 local database

## Run Locally

```bash
./mvnw spring-boot:run
```

The backend runs on:

```text
http://localhost:8080
```

Health check:

```text
GET http://localhost:8080/api/health
```

## Database

Local development uses H2 by default:

```text
jdbc:h2:file:./data/lost-then-found;AUTO_SERVER=TRUE
```

The app creates/updates tables automatically with `spring.jpa.hibernate.ddl-auto=update` and seeds demo data on first startup.

H2 console:

```text
http://localhost:8080/h2-console
```

## Environment Variables

```text
PORT=8080
DATABASE_URL=jdbc:h2:file:./data/lost-then-found;AUTO_SERVER=TRUE
DATABASE_USERNAME=sa
DATABASE_PASSWORD=
FRONTEND_URL=https://your-frontend.example.com
ADMIN_EMAIL=avery.patel@pleasantvalley.edu
SEED_DATA_ENABLED=true
```

For a future Postgres deployment, point `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD` at the hosted database and add the Postgres JDBC driver dependency.

## Frontend Integration

For local Vite proxy mode, point the frontend proxy to:

```text
http://127.0.0.1:8080
```

For split deployments, build the frontend with:

```bash
VITE_API_URL=https://your-backend.example.com npm run build
```

## API Routes

- `GET /api/health`
- `GET /api/items`
- `POST /api/items`
- `PATCH /api/items/{id}`
- `DELETE /api/items/{id}`
- `GET /api/entities/{entityName}`
- `POST /api/entities/{entityName}`
- `PATCH /api/entities/{entityName}/{id}`
- `DELETE /api/entities/{entityName}/{id}`
- `GET /api/auth/user?email={email}`
- `POST /api/auth/signin`
- `POST /api/uploads`

Supported generic entities:

- `LostReport`
- `Claim`
- `Notification`
- `AuditLog`

## Tests

```bash
./mvnw test
```

## Current Limitations

- Auth intentionally matches the current simple frontend flow; no JWT/security layer yet.
- Uploads return the submitted `data_url` through an upload service abstraction. Cloud storage can be added there later.
- Local dev uses H2. Postgres is the intended next production database step.
