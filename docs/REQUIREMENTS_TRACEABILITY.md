# Requirements Traceability (summary)

Detailed per-area status and test evidence: `docs/testing/acceptance-matrix.md`. API shapes: `docs/architecture/api-overview.md`.

| Capability | Backend package | Frontend | Tests |
|---|---|---|---|
| Registration (OTP), login, reset | `security`, `user` | `features/auth` | `AuthServiceTest`, `PasswordPolicyTest` |
| Catalog, search, filters | `catalog`, `lookup` | `features/catalog`, `shared/components` | `SlugsTest` |
| Admin catalog, images, import | `catalog`, `media`, `imports` | `features/admin/{products,product-editor,categories,lookups,imports}` | `CsvTest` |
| Inventory | `inventory` | `features/admin/inventory` | — |
| Cart, wishlist | `cart`, `wishlist` | `features/cart`, `features/wishlist` | — |
| Checkout, orders, shipments | `order`, `shipment` | `features/checkout`, `features/account`, `features/admin/orders` | `OrderStateMachineTest`, `PricingServiceTest` |
| Payments | `payment` | `features/checkout` | — |
| CMS, theme, shop profile | `shop` | `features/storefront`, `features/admin/{site-builder,shop-settings}` | — |
| Analytics | `analytics` | `features/admin/analytics` | — |
| Error centre, audit, users, dashboard | `errorcenter`, `audit`, `admin`, `user` | `features/admin/{errors,audit,users,dashboard}` | `ErrorEventSanitizeTest` |
