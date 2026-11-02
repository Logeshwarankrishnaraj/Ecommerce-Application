# Jewel Store — jewelry e-commerce (Angular 22 + Spring Boot 4 + MySQL)

Modular monolith built from `Jewelry_Ecommerce_SRS_Architecture_Plan.md`.
**Read "Status and known gaps" before relying on this build.**

```
backend/    Spring Boot (Java 21, Maven), Flyway migrations, feature-first packages under com.commerce
frontend/   Angular 22 standalone app (storefront + admin), Tailwind 4
docs/       API contract, architecture decisions, acceptance matrix, traceability
```

## Run locally
Prerequisites: JDK 21, Maven, Node 22.22.3+ (Node 24 recommended), MySQL 8 (Docker optional: `docker compose up -d` provides MySQL + MailHog; otherwise install MySQL and create database `jewel_store`, user `jewel_app` / `jewel_local_pw`).

```bash
docker compose up -d                       # MySQL + MailHog (mail UI http://localhost:8025)
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=local
cd frontend && npm install && npm start    # http://localhost:4200 (proxies /api and /media to :8080)
```
The `local` profile seeds demo accounts (**local/test only; the app refuses to start with them anywhere else**):
`admin` / `Admin@12345!` (super admin, sign in at **http://localhost:4200/admin/login**) and `customer` / `Customer@12345!` (sign in at `/login`). The two portals are separate: staff accounts are rejected on `/login` and customers on `/admin/login`. It also enables the mock payment provider
(Checkout → "Pay online" → simulate success / failure / cancel) and logs OTP emails (also visible in MailHog).

Tests: `cd backend && mvn test` · `cd frontend && npx ng test --watch=false` · `npx ng build`.
The frontend pins `npm@12` (`packageManager` in `package.json`); older npm versions fail to read the lockfile.

## Troubleshooting: is the frontend talking to the backend?
1. Start the backend with the `local` profile. In its console you must see `LOCAL CHECK: database reachable, app_users=2 ...`.
2. Start the frontend with `npm start` (restart it after any change to `angular.json` / `proxy.conf.json`).
3. Open http://localhost:4200/diagnostics and press **Run checks** (optionally enter the demo login). Each line shows what failed and why.
4. In the backend console every call prints `API <METHOD> <path> -> <status>`, and the local profile prints each SQL statement
   (a login shows `select ... from app_users ...`). No `API` lines at all = the browser is not reaching the backend (proxy not applied / backend not running / old build).
5. Always use the dev server URL (http://localhost:4200), not the backend port, so the session and CSRF cookies are same-origin.

## Verify email delivery (SMTP)
Registration OTP, password reset, order emails, low-stock alerts and error alerts all use the same mail settings.
1. Local, MailHog: `docker compose up -d`, start the backend with `MAIL_LOG_ONLY=false` and open http://localhost:8025; register a customer and place an order - you should see the OTP, order-confirmed, shipped, delivered and cancelled emails.
2. Real SMTP: set `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_AUTH=true`, `SMTP_STARTTLS=true`, `SMTP_FROM_EMAIL` and `ADMIN_ALERT_EMAILS` (comma-separated) from `.env.example`.
3. OTP and reset mail are sent immediately (a failure shows an error to the user). Order, low-stock and error mail are queued in `email_outbox` and sent every 15 s with up to 5 retries; check that table's `state` column (`SENT` / `FAILED`) if something does not arrive.
4. Low-stock alerts need `ADMIN_ALERT_EMAILS` and the setting `alerts.lowStockEmail` = true; they fire once when available stock first falls to the product's threshold.

## Production notes
* Copy `.env.example`, set real values. Schema is owned by Flyway (`ddl-auto: none`). `ProductionSafetyCheck` aborts startup if demo accounts, mock payments or log-only mail are enabled outside local/test.
* First admin: set `BOOTSTRAP_ADMIN_EMAIL` / `BOOTSTRAP_ADMIN_PASSWORD` for one start, then remove them.
* Serve the Angular `dist/` and the API under one origin (reverse proxy) so the session and CSRF cookies are same-site; set `COOKIE_SECURE=true` behind HTTPS.
* Online payments: only a dev mock exists. A real gateway needs a `PaymentProvider` implementation (see `payment/PaymentProvider`), the webhook secret, and a security review.
* Legal text (privacy, terms, shipping, returns) is placeholder content editable in Admin → Site builder and must be written/reviewed by the business.

## Status and known gaps (honest)
* **Backend was written without being compiled or run.** The build sandbox could not reach Maven Central, so `mvn compile/test` has never been executed on this code; only a javac syntax pass and careful review were possible. Expect a first-compile round of small fixes (imports, Spring Boot 4 / Jackson 3 / Hibernate 7 API differences). The Flyway SQL and unit tests (`src/test`) are likewise unexecuted.
* The Angular app **does** build (`ng build`) and its 54 unit tests pass on Node 22 and 24. It has not been exercised against a running backend.
* No end-to-end tests. See `docs/testing/acceptance-matrix.md` for per-requirement status.
* Rate limiting is in-memory (single instance). Sessions are server-side (no JWT; see `docs/architecture/decisions.md`).
* Not implemented: real payment gateway, refunds UI, coupons/discounts (field exists, always 0), multi-currency, invoices/PDF, email templates beyond plain text, i18n.

## API documentation (Swagger)

With the default `local` profile the backend serves Swagger UI at **http://localhost:8080/swagger-ui.html** (OpenAPI JSON at `/v3/api-docs`). Use the group dropdown (storefront / customer / admin / dev). To try protected endpoints, run `POST /api/v1/auth/login` there first (`customer` / `Customer@12345!` or `admin` / `Admin@12345!`); the session cookie is then sent automatically. In any other profile the docs are off unless `SWAGGER_ENABLED=true`. After pulling this change, refresh Maven in Eclipse (Project → Maven → Update Project) so the new dependency downloads.
