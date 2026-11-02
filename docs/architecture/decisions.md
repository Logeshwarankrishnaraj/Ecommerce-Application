# Architecture decisions and deviations from the SRS

| # | Decision | Why / deviation |
|---|---|---|
| 1 | Modular monolith, feature-first packages (`com.commerce.<feature>`) | One deployable, clear seams. |
| 2 | **Server session + CSRF double-submit cookie, no JWT** | Browser-only client; HttpOnly session cookie avoids token storage in JS. `SessionRevalidationFilter` rechecks user status/roles on every request so disabling a user takes effect immediately. |
| 3 | Java 21 (SRS may state another version) | LTS; Spring Boot 4.1.x supports it. |
| 4 | Flyway owns the schema; `ddl-auto: none`; V1 schema, V2 reference data | Reproducible, reviewable DB changes. |
| 5 | Stock: COD decrements at order time; online orders **reserve** stock for 30 min and commit on verified payment; `PendingPaymentExpiryJob` releases. Pessimistic row lock + immutable `inventory_movements`. | Prevents overselling under concurrency. |
| 6 | Payment provider abstraction + dev-only mock; HMAC-SHA256 webhook with idempotency table; server-side verify (browser claims are never trusted) | Real gateway can be added without touching order logic. Webhook JSON is parsed with a regex for the 3 needed fields (no JSON library dependency) — replace when a real provider is integrated. |
| 7 | Order placement is idempotent (`Idempotency-Key`, unique `(user_id, key)`) and compares `expectedTotal` with the server quote (`PRICE_CHANGED`) | Double-click/retry safety; no silent price changes. |
| 8 | Order state machine is an explicit allow-list; `PENDING_PAYMENT→PLACED` is system-only | Admins can never mark an unpaid online order paid. |
| 9 | Images: JPEG/PNG only, magic-byte + decode check, centre-crop to configured ratio, derivatives stored as base64 in MySQL (`product_image_data`) and served from `/media`, ≤5 per product | Safe uploads; consistent catalogue. |
| 10 | Errors: central `GlobalExceptionHandler` → `ApiError` with correlation id; unexpected errors recorded sanitized, de-duplicated per signature (15 min), alerted via email outbox with retry | Error centre + no PII in alerts. |
| 11 | In-memory rate limiter and account lockout counters | Adequate for one instance; use Redis or the DB if scaled out. |
| 12 | OpenAPI generation is an optional Maven profile (`-Popenapi`) | Keeps the default build dependency-light. |
| 13 | Bulk import files are stored under `<media>/imports` (private, not served) between preview and commit | Commit re-validates nothing new: it applies exactly what was previewed. |

## SQL injection defence
- All queries are JPQL (Spring Data `@Query`, derived queries, or `EntityManager.createQuery`) with named or positional bound parameters. There are no native repository queries.
- The only dynamically assembled queries (`ProductSearchService`, `StorefrontService`) concatenate server-built constants (`base`, `order`, `LIVE`); sort order is chosen from a fixed allow-list and attribute filters are checked against allow-lists first.
- `Pageable`/`Sort` are always built on the server; no request parameter is passed as a sort property.
- Every `LIKE` search goes through `Like.contains(...)` (lower-cases, escapes `%` `_` `!`) with `escape '!'`.
- JDBC multi-statement flags (`allowMultiQueries`) are not enabled. Production should connect as the limited `jewel_app` user, never `root` (the `local` profile uses root for convenience only).
- `SqlInjectionGuardTest` fails the build if a native `@Query` appears or query text is built from anything outside the reviewed fragment list.

