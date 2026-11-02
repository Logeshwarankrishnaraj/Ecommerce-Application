# API Overview (contract between backend and Angular)

Base path `/api/v1`. JSON camelCase. Money = JSON numbers with 2 decimals (BigDecimal). Timestamps = ISO-8601 UTC strings (`Instant`).
Auth = server session cookie (`JSESSIONID`, HttpOnly) + CSRF (`XSRF-TOKEN` cookie, echoed by Angular as `X-XSRF-TOKEN`; Angular `HttpClient` does this automatically for relative URLs). The dev server proxies `/api` and `/media` to `http://localhost:8080`, so everything is same-origin. Always call `GET /auth/csrf` once at app start.

## Conventions

**Error body** (all non-2xx): `{ timestamp, status, errorCode, message, path, correlationId, fieldErrors: [{field, message}] }`.
Error codes used by the UI: `VALIDATION_FAILED(400/422)`, `UNAUTHENTICATED(401)`, `ACCESS_DENIED(403)`, `NOT_FOUND(404)`, `CONFLICT/DUPLICATE_RESOURCE(409)`, `OUT_OF_STOCK(409)`, `PRICE_CHANGED(409)`, `BUSINESS_RULE(422)`, `RATE_LIMITED(429)`, `EMAIL_DELIVERY_FAILED(503)`, `INTERNAL_ERROR(500)`.
**Page**: `{ content: T[], page, size, totalElements, totalPages }`. Query params `page` (0-based) & `size` (capped at 50 public / 100 admin).
`Image`: `{ imageId, url, thumbUrl, alt, sortOrder, primary }` (url like `/media/<key>`).
`ProductCard`: `{ productId, slug, sku, title, price, salePrice|null, effectivePrice, currency, primaryImage: Image|null, inStock, categoryName }`.
`AccountMe`: `{ userId, fullName, username, email, mobileNumber, emailVerified, roles: string[] }` (roles e.g. `ROLE_CUSTOMER`, `ROLE_ADMIN`, `ROLE_SUPER_ADMIN`).
`Address`: `{ addressId, recipientName, phone, line1, line2, locality, city, state, postalCode, country, isDefault }`.
`OrderAddress`: same without `addressId`/`isDefault`.

## Auth / account (public unless noted)
| Route | Body → Response |
|---|---|
| `GET /auth/csrf` | → 204 |
| `POST /auth/registration/request-otp` | `{fullName, username, email, mobileNumber}` → 202 `{challengeId, expiresInSeconds, resendAfterSeconds}` |
| `POST /auth/registration/resend-otp` | `{challengeId}` → 202 same shape |
| `POST /auth/registration/verify-and-complete` | `{challengeId, otp, password}` → 201 `AccountMe` (session started) |
| `POST /auth/login` | `{usernameOrEmail, password}` → 200 `AccountMe` |
| `POST /auth/logout` | → 204 |
| `POST /auth/password-reset/request` | `{email}` → 202 `{message}` (always generic) |
| `POST /auth/password-reset/complete` | `{email, code, newPassword}` → 204 |
| `GET /account/me` | → `AccountMe` (401 if anonymous) |
| `PATCH /account/me` | `{fullName?, mobileNumber?}` → `AccountMe` |
| `POST /account/change-password` | `{currentPassword, newPassword}` → 204 |
| `GET/POST /account/addresses`, `PATCH/DELETE /account/addresses/{id}` | `Address` (POST/PATCH body = Address fields) |

Password policy: ≥ 10 chars, upper, lower, digit, symbol. OTP: 6 digits.

## Storefront (public)
- `GET /storefront/config` → `{ theme:{bg,surface,text,accent,font}, shop:{displayName,tagline,logoUrl,supportEmail,supportPhone,address}, headerLinks:[{label,url}], footerLinks:[{label,url}], currency, paymentMethods:["COD","ONLINE"], mockPayments:boolean, imageRatio:"1:1", cartMaxQty }`
- `GET /storefront/home` → `{ blocks: Block[] }`; `GET /storefront/pages/{slug}` → `{ slug, title, blocks: Block[] }`
- `Block`: `{ blockId, type: HERO|CATEGORY_TILES|FEATURED_PRODUCTS|PROMO_STRIP|TEXT_IMAGE, title, subtitle, body, imageUrl, linkUrl, ctaLabel, products: ProductCard[], categories: CategoryNode[] }` (`products`/`categories` filled server-side for the relevant block types)
- `GET /categories` → `CategoryNode[]` tree `{categoryId,name,slug,description,children:CategoryNode[]}`
- `GET /lookup-groups/{groupCode}/values` → `[{valueCode, displayLabel}]`
- `GET /products/filters` → `{ attributes:[{code,label,values:[{valueCode,displayLabel}]}], priceRange:{min,max} }`
- `GET /products?q&category(slug)&minPrice&maxPrice&sort=newest|price_asc|price_desc|title&inStock=true&attr=material:gold&attr=color:red&page&size` → `Page<ProductCard>`
- `GET /products/{slug}` → `ProductDetail` = ProductCard + `{ description, categoryId, categorySlug, images: Image[], attributes:[{code,label,valueCode,valueLabel}], stockStatus: IN_STOCK|LOW_STOCK|OUT_OF_STOCK, availableQuantity (capped 10), seoTitle, seoDescription }`
- `GET /products/{productId}/related` → `ProductCard[]`
- `POST /products/{productId}/interactions/view` → 204

## Wishlist / cart (authenticated)
- `GET /account/wishlist` → `{ items:[{productId, addedAt, product: ProductCard}] }`; `POST /account/wishlist/items {productId}` → wishlist; `DELETE /account/wishlist/items/{productId}` → wishlist
- `GET /account/cart` → `Cart` = `{ items:[{cartItemId, productId, slug, title, sku, imageUrl, unitPrice, quantity, lineTotal, available, availableQuantity, issue|null}], subtotal, itemCount, hasIssues, currency }`
- `POST /account/cart/items {productId, quantity}` (adds to existing qty) → Cart; `PATCH /account/cart/items/{cartItemId} {quantity}` → Cart; `DELETE /account/cart/items/{cartItemId}` → Cart
- `POST /account/cart/checkout-preview {}` → `Quote` = `{ items:[{productId,sku,title,unitPrice,quantity,lineTotal,imageUrl}], subtotal, shippingAmount, taxAmount, discountAmount, total, currency, issues:[{productId,code,message}], canCheckout }`

## Checkout / payment / orders (authenticated)
- `POST /checkout/orders` (header `Idempotency-Key: <uuid>` required) body `{addressId, paymentMethod:"COD"|"ONLINE", expectedTotal, acceptedTerms:true}` → 201 `OrderDetail` plus `payment:{paymentId,status,provider,mock,requiresAction}`. Server errors: `OUT_OF_STOCK`, `PRICE_CHANGED` (409, body message contains new total), `BUSINESS_RULE`.
- `POST /payments/{paymentId}/verify` → `{paymentId, status, orderId, orderNumber, orderStatus}` (status `INITIATED|PAID|FAILED|CANCELLED`; server asks the provider — never trusts the browser)
- `POST /payments/webhooks/{providerCode}` header `X-Signature` (HMAC-SHA256 hex of raw body) → 204
- `POST /dev/mock-payments/{paymentId}/outcome {outcome:"SUCCESS"|"FAILURE"|"CANCEL"}` → 204 — **local/test profiles only**; simulates the gateway side.
- `GET /account/orders?page&size` → `Page<{orderId,orderNumber,status,paymentMethod,paymentStatus,total,currency,createdAt,itemCount}>`
- `GET /account/orders/{orderId}` → `OrderDetail` = `{ orderId, orderNumber, status, paymentMethod, paymentStatus, subtotal, shippingAmount, taxAmount, discountAmount, total, currency, createdAt, shippingAddress: OrderAddress, items:[{orderItemId,productId,sku,title,unitPrice,quantity,lineTotal,imageUrl}], shipments:[{shipmentId,carrier,trackingNumber,status,shippedAt,deliveredAt,trackingNote}], statusHistory:[{status,at}], cancellable:boolean }`
- `POST /account/orders/{orderId}/cancel` → `OrderDetail`

Order statuses: `PENDING_PAYMENT, PLACED, CONFIRMED, PACKING, SHIPPED, DELIVERED, CANCELLED`. Payment statuses: `PENDING_COLLECTION` (COD), `COLLECTED`, `INITIATED`, `PAID`, `FAILED`, `CANCELLED`, `REFUNDED`. Shipment statuses: `PENDING, IN_TRANSIT, OUT_FOR_DELIVERY, DELIVERED`.

## Admin (ROLE_ADMIN / ROLE_SUPER_ADMIN; all under `/admin`)
- `GET /admin/dashboard/summary` → `{ timezone, orderCountsByStatus:{STATUS:n}, grossOrderValue, productCount, lowStockCount, openErrorCount, recentErrors:[ErrorRow], interactions:{from,to,views,wishlistAdds,cartAdds} }`
- Products: `GET /admin/products?q&status&categoryId&page&size` → `Page<{productId,sku,title,slug,price,salePrice,status,categoryName,onHand,reserved,available,primaryImageUrl,updatedAt}>`; `POST /admin/products` / `PATCH /admin/products/{id}` body `ProductUpsert={sku,title,slug?,description,categoryId,basePrice,salePrice|null,currency?,seoTitle?,seoDescription?,lowStockThreshold?,initialStock?(create only),version?(patch),attributes:[{code,valueCode?,textValue?}]}` → `AdminProduct`; `GET /admin/products/{id}` → `AdminProduct` = ProductUpsert fields + `{productId,status,images:Image[],onHand,reserved,available,version,createdAt,updatedAt}`; `POST /admin/products/{id}/publish|unpublish|archive` → AdminProduct
- Images: `POST /admin/products/{id}/images` multipart `file`, `altText` → AdminProduct; `PATCH /admin/products/{id}/images/order {imageIds:[..]}` → AdminProduct; `PATCH /admin/products/{id}/images/{imageId} {altText?, primary?}`; `DELETE /admin/products/{id}/images/{imageId}`. Max 5; JPEG/PNG only.
- Categories: `GET /admin/categories` → `[{categoryId,parentCategoryId,name,slug,description,active,sortOrder,productCount}]`; `POST`, `PATCH /{id}` body `{parentCategoryId,name,slug?,description,active,sortOrder}`; `DELETE /{id}` (409 if it has products/children)
- Lookups: `GET /admin/lookups` → `[{groupCode,label,valueCount}]`; `GET /admin/lookups/{groupCode}` → `[{lovId,valueCode,displayLabel,active,sortOrder}]`; `POST /admin/lookups/{groupCode}/values {valueCode,displayLabel,sortOrder}`; `PATCH /admin/lookups/values/{id} {displayLabel?,active?,sortOrder?}`; `DELETE /admin/lookups/values/{id}`; `GET /admin/attribute-definitions` → `[{code,label,lovGroupCode,filterable}]`
- Inventory: `GET /admin/inventory?q&lowOnly&page&size` → `Page<{productId,sku,title,onHand,reserved,available,lowStockThreshold,low}>`; `POST /admin/inventory/adjustments {productId,quantityDelta,reason}` → row; `GET /admin/inventory/{productId}/movements?page&size` → `Page<{movementId,quantityDelta,reason,referenceType,referenceId,actor,createdAt}>`
- Imports: `GET /admin/imports/products/template` (text/csv); `POST /admin/imports/products/preview` multipart `file` (csv), optional `images` (zip), `mode=CREATE|UPDATE`, `allowPartial=false|true` → `ImportJob`; `POST /admin/imports/products/{jobId}/commit` → `ImportJob`; `GET /admin/imports/{jobId}` → `ImportJob`; `GET /admin/imports/{jobId}/errors.csv`. `ImportJob = {jobId,status:PREVIEWED|COMMITTED|FAILED,mode,allowPartial,filename,totalRows,validRows,invalidRows,committedRows,createdAt,rows:[{rowNumber,sku,valid,result,errors:[{column,message}]}]}`
- Orders: `GET /admin/orders?status&q&page&size` → `Page<{orderId,orderNumber,customerName,status,paymentMethod,paymentStatus,total,createdAt}>`; `GET /admin/orders/export?status&q` → `text/csv` download (UTF-8 BOM, formula-safe cells, newest first, max 5000 rows, audited as `ORDER_EXPORT`); `GET /admin/orders/{id}` → `OrderDetail` + `{customer:{name,email,phone}, allowedTransitions:[status], packingNotes}`; `POST /admin/orders/{id}/status {status,note?}`; `POST /admin/orders/{id}/shipments {carrier,trackingNumber,status?,trackingNote?}`; `PATCH /admin/shipments/{id} {carrier?,trackingNumber?,status?,trackingNote?}`; `PATCH /admin/orders/{id}/packing-notes {packingNotes}`
- Analytics: `GET /admin/analytics/products?from=YYYY-MM-DD&to=YYYY-MM-DD&limit` → `{from,to,timezone,totals:{views,wishlistAdds,cartAdds},products:[{productId,sku,title,views,wishlistAdds,cartAdds}],daily:[{date,views,wishlistAdds,cartAdds}]}`
- Shop: `GET/PUT /admin/shop-profile` `{displayName,tagline,logoUrl,supportEmail,supportPhone,senderDisplayName,addressLine1,addressLine2,city,state,postalCode,country,returnAddress,handlingTimeDays}`
- Settings: `GET /admin/site-settings` → `[{key,value,group,type,description,options|null}]`; `PATCH /admin/site-settings {values:{key:value}}` → same list. Keys: `theme.bg, theme.surface, theme.text, theme.accent` (hex), `theme.font` (`SERIF|SANS|MIXED`), `media.aspectRatio` (`1:1|4:5|3:4`), `currency.code`, `shipping.flatFee`, `shipping.freeOver`, `tax.ratePercent`, `payments.cod.enabled`, `payments.online.enabled`, `inventory.lowStockDefault`, `cart.maxQty`, `orders.terms`.
- Site links: `GET/PUT /admin/site-links` `[{placement:HEADER|FOOTER,label,url,sortOrder,active}]`
- Content: `GET /admin/content-pages` → `[{pageId,slug,title,published,blocks:[AdminBlock]}]`; `POST /admin/content-pages {slug,title,published}`; `PATCH /admin/content-pages/{id} {title,published,blocks:[AdminBlock]}` (replaces block list) where `AdminBlock={blockId?,type,title,subtitle,body,imageUrl,linkUrl,ctaLabel,productRule:NEWEST|FEATURED|BEST_SELLERS|CATEGORY,categoryId,itemLimit,sortOrder,published}`. The home page is the page with slug `home`. Plain text only — `<` and `>` are rejected.
- Errors: `GET /admin/errors?status&severity&page&size` → `Page<ErrorRow>`; `ErrorRow={eventId,correlationId,severity,occurredAt,lastOccurredAt,occurrenceCount,operation,route,errorType,summary,notificationState,remediationState}`; `GET /admin/errors/{id}`; `POST /admin/errors/{id}/acknowledge`
- Audit: `GET /admin/audit-logs?action&actor&page&size` → `Page<{auditId,actor,action,targetType,targetId,summary,outcome,correlationId,createdAt}>`
- Users: `GET /admin/users?q&role&status&page&size` → `Page<{userId,fullName,username,email,status,roles,createdAt}>`; `POST /admin/users/{id}/status {status:ACTIVE|DISABLED}`; `POST /admin/users/{id}/roles {role:"ROLE_ADMIN", grant:true|false}` (SUPER_ADMIN only)

## Media
`GET /media/{key}` public, immutable cache headers. Keys are random; derivatives are `<key>.jpg` (large) and `<key>_t.jpg` (thumb). The bytes are stored as base64 text in MySQL (`product_image_data`) and decoded on request; nothing is written to disk.
