# Implementation plan and status

| Task | Scope | Status |
|---|---|---|
| T1 | Foundation: Maven project, config, Flyway schema + reference data, security, error handling | Written (uncompiled) |
| T2 | Auth & accounts | Written (uncompiled); UI done |
| T3 | Catalog, lookups, images, inventory, analytics | Written (uncompiled); UI done |
| T4 | Cart, wishlist, storefront CMS, shop settings | Written (uncompiled); UI done |
| T5 | Checkout, payments (mock), orders, shipments | Written (uncompiled); UI done |
| T6 | Admin dashboard, bulk import, error centre, audit, users | Written (uncompiled); UI done |
| T7 | Docs, compose, README, bootstrap admin | Done |
| T8 (next) | Compile + run backend tests, fix issues; E2E; real payment gateway; production hardening review | **Not started** |

## Remaining work, in phases (from the SRS "To be implemented" list)

| Phase | Scope | Status |
|---|---|---|
| A. Make it run | Compile, `mvn test`, start on an empty DB, fix first-compile errors (V4 migration, media controller, audit delete, springdoc version) | **Blocked in the build sandbox (Maven Central unreachable); do locally in Eclipse/CLI** |
| B. Prove the core flow | Register -> cart -> COD and mock checkout -> admin confirm/pack/ship/deliver -> cancel; then Playwright E2E | Not started (needs Phase A) |
| C. Notifications | OTP + error mail via SMTP (verify); customer order emails; low-stock admin alert | Order emails and low-stock alert **written** (unexecuted); SMTP check is a manual step, see README |
| D. Admin operations | Order CSV export | **Written** (backend unexecuted, UI built and tested) |
| E. Customer service | Return/refund requests (schema in V3), coupons (schema in V3) | Not started |
| F. Go-live | Real payment provider behind `PaymentProvider`, legal text review | Not started (needs provider choice and credentials) |

