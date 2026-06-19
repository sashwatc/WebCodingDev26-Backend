# Lost Then Found Backend

Spring Boot backend for the Lost Then Found Vite/React frontend.

## Stack

- Java 21
- Spring Boot 4.1.0
- Maven
- Spring Web MVC
- Spring Data MongoDB
- Validation
- MongoDB Atlas, database `lostthenfound`

## MongoDB Setup

Create a MongoDB Atlas cluster and set these environment variables before running the app:

```env
MONGO_URI=mongodb+srv://USERNAME:PASSWORD@lostthenfound.3rv5ips.mongodb.net/lostthenfound?retryWrites=true&w=majority&appName=LostThenFound
MONGO_DATABASE=lostthenfound
```

Do not include angle brackets around the real password, and do not commit `.env`, `.env.local`, or other local secret files.

The production/default Spring profile expects `MONGO_URI` to be present. For a safe local profile example, see `src/main/resources/application-local.properties`.

Because this repo is on Spring Boot 4.1, Mongo connection settings are wired through Spring Boot's `spring.mongodb.*` properties while still reading the same `MONGO_URI` and `MONGO_DATABASE` environment variables.

## Run Locally

```bash
export MONGO_URI="mongodb+srv://USERNAME:PASSWORD@lostthenfound.3rv5ips.mongodb.net/lostthenfound?retryWrites=true&w=majority&appName=LostThenFound"
export MONGO_DATABASE=lostthenfound
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

Expected shape:

```json
{
  "status": "ok",
  "database": "mongodb",
  "connected": true
}
```

## Environment Variables

```text
PORT=8080
MONGO_URI=mongodb+srv://USERNAME:PASSWORD@lostthenfound.3rv5ips.mongodb.net/lostthenfound?retryWrites=true&w=majority&appName=LostThenFound
MONGO_DATABASE=lostthenfound
FRONTEND_URL=https://your-frontend.example.com
ADMIN_EMAIL=avery.patel@pleasantvalley.edu
SEED_DATA_ENABLED=true
```

## Data

MongoDB collections:

- `found_items`
- `lost_reports`
- `claims`
- `notifications`
- `audit_logs`
- `users`

Seed data is inserted only when the `found_items` collection is empty and `SEED_DATA_ENABLED` is true.

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

Tests mock the service layer where appropriate so Atlas is not required during CI/local verification.

```bash
./mvnw test
```

## Current Limitations

- Auth intentionally matches the current simple frontend flow; no JWT/security layer yet.
- Uploads return the submitted `data_url` through an upload service abstraction. Cloud storage can be added there later.
