# Acceptance matrix (honest status)

Legend: **Impl** = code written; **Unit** = automated test written (not executed unless noted); **E2E** = verified against a running stack (none yet).

| Area | Requirement | Impl | Automated tests | E2E |
|---|---|---|---|---|
| Auth | OTP registration, login, lockout (5/15 min), password reset, password policy | Yes | `AuthServiceTest`, `PasswordPolicyTest` (not executed); frontend `validators.spec` (passing) | No |
| Auth | CSRF, session revalidation, role-based `/admin` | Yes | — | No |
| Catalog | Categories tree, product search/filter/sort, safe LIKE + sort allow-list | Yes | `SlugsTest` | No |
| Catalog | Admin product CRUD, lifecycle, ≤5 images, crop/derivatives | Yes | — | No |
| Lookups | Admin-managed filter values / attributes | Yes | — | No |
| Inventory | Adjustments with reason, immutable movements, low-stock | Yes | — | No |
| Cart / wishlist | Per-user, server-validated | Yes | — | No |
| Cart | Visitor (guest) cart in the browser, merged into the account cart at sign-in (trimmed to the per-item maximum; checkout waits for the merge) | Yes | `guest-cart.spec` (29 cases incl. merge planning, limits, tampered storage) | Browser-verified against a mock API (not the real backend) |
| Cart / wishlist | "Add to cart" on product cards; "Move to cart" on the wishlist | Yes | — | Browser-verified against a mock API |
| Auth | Session expiry: notice, sign-out, redirect to the right sign-in with return URL on private pages; stay put on public pages | Yes | `session-expiry.spec` (interceptor + redirect rules) | Browser-verified against a mock API |
| Checkout | Quote, price-changed check, idempotency, COD + online (mock) | Yes | `PricingServiceTest` | No |
| Orders | State machine, customer cancel rules, admin transitions, shipments | Yes | `OrderStateMachineTest` | No |
| Payments | Verify, webhook HMAC + idempotency, expiry job | Yes (mock only) | — | No |
| Notifications | Customer emails on order placed / shipped / delivered / cancelled (outbox, same transaction) | Yes | `OrderNotifierTest` (not executed) | No |
| Notifications | Admin low-stock email on first drop to threshold (honours `alerts.lowStockEmail`, `ADMIN_ALERT_EMAILS`) | Yes | `LowStockAlerterTest` (not executed) | No |
| Orders | Admin CSV export of filtered orders (formula-safe, capped, audited) | Yes | `CsvTest` covers cell escaping; frontend `order-export.spec` passing | No |
| CMS | Home/info pages, blocks, links, theme settings, shop profile | Yes | — | No |
| Analytics | De-duplicated views / wishlist / cart events, admin report | Yes | — | No |
| Import | CSV + ZIP, preview, all-or-nothing commit, errors.csv | Yes | `CsvTest` | No |
| Security | SQL injection: all user input bound as query parameters; sort from allow-list; escaped LIKE in every search; source guard test | Yes | `SqlInjectionGuardTest` (not executed; logic verified in a harness incl. a planted-vulnerability check) | No |
| Ops | Error centre, audit log, user admin, dashboard | Yes | `ErrorEventSanitizeTest`, `ProductionSafetyCheckTest` | No |
| Frontend | Storefront + admin pages build | Yes | 54 unit tests passing, `ng build` passing | No |

**Next verification steps:** run `mvn test`, fix compile issues; start the stack; walk the demo flow (register → add to cart → COD and mock-online checkout → admin confirm/pack/ship/deliver → cancel/refund); add Playwright E2E.
