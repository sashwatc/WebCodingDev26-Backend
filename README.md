# Lost Then Found

School lost-and-found recovery platform built for FBLA Website Coding & Development / NLC-level judging.

## Architecture Inspected

- Frontend: this checkout did not include the referenced sibling Vite/React app, so the upgraded demo UI is a focused static HTML/CSS/JavaScript app served from Spring Boot `src/main/resources/static`.
- Browser auth: Appwrite Web SDK handles email/password signup and login, Google OAuth, current-session lookup, email verification, verification resend, and logout.
- Student-admin chat: Appwrite Databases stores chat conversations and messages, with Appwrite Realtime subscriptions for live refresh when supported by the browser/network.
- Routing: browser routes use the History API, with Spring MVC forwarding app and auth routes such as `/chat`, `/admin/chat`, `/login`, `/signup`, `/verify-email`, and `/auth/callback` to `index.html`.
- Backend: Java 21, Spring Boot 4.1, Spring Web MVC, Bean Validation, Spring Data MongoDB.
- Database: MongoDB Atlas or local MongoDB through `MONGO_URI` and `MONGO_DATABASE`.
- Core models: `FoundItem`, `LostReport`, `Claim`, `AppUser`, `Notification`, `AuditLog`, `MatchSuggestion`.
- Auth: Appwrite protects browser routes. `ADMIN_EMAIL` still becomes the backend admin account when the signed-in Appwrite email syncs through `/api/auth/signin`.

## Local Setup

```bash
cp .env.example .env
```

Fill in `MONGO_URI`, `MONGO_DATABASE`, `ADMIN_EMAIL`, and the Appwrite `VITE_APPWRITE_*` values. For a local MongoDB profile:

```bash
SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run
```

Default app URL:

```text
http://localhost:8080
```

Health check:

```text
GET http://localhost:8080/api/health
```

## Scripts

```bash
./mvnw spring-boot:run
./mvnw test
npm run backend
npm run dev
```

`npm run backend` and `npm run dev` both delegate to Spring Boot in this repo.

## Environment Variables

```env
MONGO_URI=fill_this_in
MONGO_DATABASE=lostthenfound
PORT=8080
FRONTEND_URL=http://localhost:8080
ADMIN_EMAIL=avery.patel@pleasantvalley.edu
VITE_APPWRITE_ENDPOINT=https://cloud.appwrite.io/v1
VITE_APPWRITE_PROJECT_ID=fill_this_in
VITE_APPWRITE_DATABASE_ID=fill_this_in
VITE_APPWRITE_CHAT_CONVERSATIONS_COLLECTION_ID=chat_conversations
VITE_APPWRITE_CHAT_MESSAGES_COLLECTION_ID=chat_messages
VITE_APPWRITE_ADMIN_TEAM_ID=fill_this_in
SEED_DATA_ENABLED=true
AI_MATCHMAKING_ENABLED=true
AI_API_KEY=
AI_MATCHMAKING_BASE_URL=https://api.openai.com/v1/chat/completions
AI_MATCHMAKING_MODEL=gpt-4o-mini
AI_MATCHMAKING_MAX_CANDIDATES=8
AI_MATCHMAKING_MIN_CONFIDENCE=35
EMAIL_DEMO_MODE=true
EMAIL_FROM=lostthenfound@pvhs.demo
PICKUP_LOCATION=PVHS Main Office pickup station
PICKUP_HOURS=School days, 8:00 AM-3:30 PM
```

`AI_API_KEY` is optional. If it is missing or AI matchmaking is disabled, the backend uses the local explainable scorer.

`EMAIL_DEMO_MODE=true` logs safe email previews and saves notifications without requiring SMTP, SendGrid, or a paid service.

Appwrite setup:

- Add your deployed site URL and local URL, such as `http://localhost:8080`, as Web platforms in the Appwrite project.
- Enable Email/Password auth and the Google OAuth provider in Appwrite Auth settings.
- In Google OAuth settings, use the redirect URL Appwrite shows in its provider setup modal.
- Appwrite verification emails redirect back to `/verify-email`, where the app reads `userId` and `secret`.
- Create an Appwrite database with two collections: `chat_conversations` and `chat_messages`.
- Enable row security on both chat collections.
- Give table-level `create` permission to authenticated users, and table-level `read` plus `update` permissions to the Appwrite admin team.
- Set `VITE_APPWRITE_ADMIN_TEAM_ID` to that team ID and add admin users to the team.

Chat collection attributes:

`chat_conversations`: `conversationId`, `studentId`, `studentName`, `studentEmail`, `adminTeamId`, `lastMessageText`, `lastMessageAt`, `studentUnreadCount`, `adminUnreadCount`, `createdAt`, `updatedAt`.

`chat_messages`: `messageId`, `conversationId`, `senderId`, `senderName`, `senderRole`, `messageText`, `createdAt`, `read`.

Recommended indexes: `studentId`, `conversationId`, `senderRole`, `read`, `updatedAt`, and `createdAt`.

## Web Routes

- `/` - Home and judge-ready workflow overview
- `/report-lost` - Student lost report form with possible matches after submit
- `/report-found` - Found item intake with private verification clue and photo validation
- `/browse` - Public-safe found item cards
- `/claim` - Claim form with secret ownership detail
- `/admin` - Admin dashboard for claims, reports, items, archive actions, and audit log
- `/chat` - Student-to-admin conversation
- `/admin/chat` - Admin chat dashboard with all permitted student conversations
- `/login` - Appwrite email/password and Google sign-in
- `/signup` - Appwrite email/password signup with immediate session and verification email
- `/verify-email` - Appwrite email verification callback
- `/auth/callback` - Google OAuth success callback
- `/sources` - Sources and license notes

## API Routes

- `GET /api/items`
- `GET /api/items/{id}`
- `POST /api/items`
- `PATCH /api/items/{id}`
- `DELETE /api/items/{id}`
- `GET /api/admin/items`
- `GET /api/admin/dashboard`
- `GET /api/admin/lost-reports`
- `GET /api/admin/claims`
- `GET /api/admin/audit-logs`
- `GET /api/admin/notifications`
- `GET /api/admin/recovery-center`
- `POST /api/admin/claims/{id}/approve`
- `POST /api/admin/claims/{id}/deny`
- `POST /api/admin/items/{id}/archive`
- `POST /api/admin/recovery-cases`
- `POST /api/admin/recovery-cases/lost-reports/{lostReportId}`
- `POST /api/recovery-cases/lost-reports/{lostReportId}/refresh`
- `POST /api/recovery-cases/{id}/assign`
- `POST /api/recovery-cases/{id}/missions`
- `PATCH /api/recovery-missions/{id}`
- `GET /api/sentinel/alerts`
- `POST /api/sentinel/recompute`
- `POST /api/sentinel/alerts/{id}/acknowledge`
- `POST /api/sentinel/alerts/{id}/dismiss`
- `POST /api/sentinel/alerts/{id}/resolve`
- `GET /api/sentinel/alerts/{id}/source-reports`
- `POST /api/sentinel/alerts/{id}/mission`
- `POST /api/admin/demo-scenarios/{airpods_gym|gym_electronics_pattern|library_water_bottle|custom}`
- `GET /api/entities/{entityName}`
- `POST /api/entities/{entityName}`
- `PATCH /api/entities/{entityName}/{id}`
- `DELETE /api/entities/{entityName}/{id}`
- `GET /api/matches/lost-reports/{id}`
- `POST /api/matches/lost-reports/{id}/refresh`
- `POST /api/matches/found-items/{id}/refresh`
- `GET /api/auth/user?email={email}`
- `POST /api/auth/signin`
- `POST /api/uploads`

Supported generic entities: `LostReport`, `Claim`, `Notification`, `AuditLog`.

Admin routes require the `X-Demo-User-Email` header and an admin user seeded or signed in with `ADMIN_EMAIL`.

## Matching Workflow

When a lost report is created, `MatchmakingService` compares it against eligible found items. The local scorer is intentionally easy to explain:

- Category match: large score
- Brand and color match: medium score
- Description keyword overlap: fuzzy score
- Location similarity: fuzzy score
- Date proximity: within 1, 3, or 7 days
- Tags: extra supporting score

Matches above the configured confidence threshold are saved on the lost report and displayed as "Possible Matches."

## Claim Verification

Public found-item cards redact sensitive data such as storage location, finder contact, internal item code, and private verification clues.

Item statuses used by the upgraded workflow:

- `FOUND`
- `CLAIM_PENDING`
- `VERIFIED`
- `ARCHIVED`

Claims are stored separately in the `claims` collection. Students submit an identifying detail, and admins approve or deny it from `/admin`.

## Recovery Center And Pattern Review

Recovery Cases always link to real Lost Reports. Admins can create a case from an existing Lost Report or create a Lost Report first through the Recovery Center. Lost Reports never create Found Items; Found Items remain real inventory.

Pattern Review powered by Loss Sentinel analyzes actual Lost Reports only. Recompute returns `not_enough_data` unless there are at least 3 recent Lost Reports, at least 2 baseline Lost Reports, and a 2x normalized baseline increase. Alerts store source Lost Report IDs, date windows, counts, reasons, suggested actions, and calculation time.

## Security And Validation

- Recursive backend sanitization strips HTML/script tags from submitted text.
- Lost reports, found items, and claim forms validate required fields.
- Uploads allow only `jpg`, `jpeg`, `png`, or `webp`.
- Uploads are rejected above 2MB.
- Admin routes use role-based demo access through the current auth system.
- Public DTOs do not expose private verification clues, finder contact, storage location, or item codes.
- Email and AI configuration stay in environment variables.

## Seeded Demo Data

Seed data is inserted when `SEED_DATA_ENABLED=true` and `found_items` is empty.

Examples included:

- Lost calculator report
- Found AirPods report
- Pending AirPods claim
- Approved calculator claim
- Admin user: `avery.patel@pleasantvalley.edu`

Admin demo scenario buttons can add:

- AirPods at Gym: Lost Report, Recovery Case, gym missions, Found Item, and pending Claim
- Gym Electronics Pattern: enough realistic Lost Reports for a valid Pattern Review alert
- Library Water Bottle: low-priority Recovery Case and mission

Scenario-created records are normal Mongo records flagged with `is_demo=true`. The scenario system never wipes non-demo user data.

## Judge Demo Flow

1. Open `http://localhost:8080`.
2. Go to `Report Lost Item` and submit the default calculator report.
3. Show the `Possible Matches` result and explain the scoring reasons.
4. Click `Claim This Item` and submit a secret ownership detail.
5. Open `Admin Dashboard` and sign in as `Avery Patel` with `avery.patel@pleasantvalley.edu`.
6. Open `Demo Scenarios`, create `AirPods at Gym`, then open `Recovery Center` and show the linked Lost Report, missions, next action, and pending claim.
7. Approve the pending claim and show the item status change, the Notifications tab email preview, and the audit log entry.
8. Create `Gym Electronics Pattern`, open `Pattern Review`, recompute, show source Lost Report IDs, then create a Recovery Mission from the alert.
9. Archive a resolved item from the admin found-items tab.

## Deployment

Deploy as a standard Spring Boot application:

```bash
./mvnw clean package
java -jar target/WebCodingDev26Backend-0.0.1-SNAPSHOT.jar
```

Set production environment variables in the host platform instead of committing secrets. The static web UI is packaged into the same Spring Boot jar.

## Verification

```bash
./mvnw test
```

Current verification: 40 tests passing.
