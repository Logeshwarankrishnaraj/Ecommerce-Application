-- Jewelry store schema (SRS section 6). MySQL 8.4+/9.x, InnoDB, utf8mb4.
-- Retention: registration/password challenges purged after 7 days, interaction events 400 days,
-- error events 180 days, audit logs retained (never cascade-deleted).

CREATE TABLE app_users (
  user_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  full_name VARCHAR(150) NOT NULL,
  username VARCHAR(50) NOT NULL,
  email VARCHAR(254) NOT NULL,
  mobile_number VARCHAR(20) NULL,
  password_hash VARCHAR(100) NOT NULL,
  email_verified_at DATETIME(6) NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  failed_login_count INT NOT NULL DEFAULT 0,
  locked_until DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT uq_app_users_username UNIQUE (username),
  CONSTRAINT uq_app_users_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE roles (
  role_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  role_code VARCHAR(40) NOT NULL,
  display_name VARCHAR(80) NOT NULL,
  CONSTRAINT uq_roles_code UNIQUE (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE user_roles (
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  PRIMARY KEY (user_id, role_id),
  CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES app_users(user_id),
  CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles(role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE registration_challenges (
  challenge_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  full_name VARCHAR(150) NOT NULL,
  username VARCHAR(50) NOT NULL,
  email VARCHAR(254) NOT NULL,
  mobile_number VARCHAR(20) NOT NULL,
  otp_hash VARCHAR(100) NOT NULL,
  expires_at DATETIME(6) NOT NULL,
  attempts INT NOT NULL DEFAULT 0,
  resend_count INT NOT NULL DEFAULT 0,
  last_sent_at DATETIME(6) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  created_at DATETIME(6) NOT NULL,
  INDEX ix_reg_email (email, status),
  INDEX ix_reg_username (username, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE password_reset_challenges (
  challenge_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  code_hash VARCHAR(100) NOT NULL,
  expires_at DATETIME(6) NOT NULL,
  attempts INT NOT NULL DEFAULT 0,
  consumed_at DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL,
  CONSTRAINT fk_prc_user FOREIGN KEY (user_id) REFERENCES app_users(user_id),
  INDEX ix_prc_user (user_id, consumed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE user_addresses (
  address_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  recipient_name VARCHAR(150) NOT NULL,
  phone VARCHAR(20) NOT NULL,
  line1 VARCHAR(200) NOT NULL,
  line2 VARCHAR(200) NULL,
  locality VARCHAR(120) NULL,
  city VARCHAR(100) NOT NULL,
  state VARCHAR(100) NOT NULL,
  postal_code VARCHAR(12) NOT NULL,
  country VARCHAR(60) NOT NULL,
  is_default BOOLEAN NOT NULL DEFAULT FALSE,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  CONSTRAINT fk_addr_user FOREIGN KEY (user_id) REFERENCES app_users(user_id),
  INDEX ix_addr_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE categories (
  category_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  parent_category_id BIGINT NULL,
  name VARCHAR(100) NOT NULL,
  slug VARCHAR(120) NOT NULL,
  description VARCHAR(500) NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  sort_order INT NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT uq_categories_slug UNIQUE (slug),
  CONSTRAINT fk_categories_parent FOREIGN KEY (parent_category_id) REFERENCES categories(category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE lov_groups (
  group_code VARCHAR(50) NOT NULL PRIMARY KEY,
  label VARCHAR(100) NOT NULL,
  public_group BOOLEAN NOT NULL DEFAULT TRUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE list_of_values (
  lov_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  group_code VARCHAR(50) NOT NULL,
  value_code VARCHAR(50) NOT NULL,
  display_label VARCHAR(100) NOT NULL,
  metadata_json VARCHAR(1000) NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  sort_order INT NOT NULL DEFAULT 0,
  CONSTRAINT uq_lov UNIQUE (group_code, value_code),
  CONSTRAINT fk_lov_group FOREIGN KEY (group_code) REFERENCES lov_groups(group_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE product_attribute_definitions (
  attribute_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(50) NOT NULL,
  label VARCHAR(100) NOT NULL,
  lov_group_code VARCHAR(50) NULL,
  filterable BOOLEAN NOT NULL DEFAULT TRUE,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  sort_order INT NOT NULL DEFAULT 0,
  CONSTRAINT uq_attr_code UNIQUE (code),
  CONSTRAINT fk_attr_group FOREIGN KEY (lov_group_code) REFERENCES lov_groups(group_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE products (
  product_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  sku VARCHAR(64) NOT NULL,
  title VARCHAR(200) NOT NULL,
  slug VARCHAR(220) NOT NULL,
  description TEXT NULL,
  category_id BIGINT NOT NULL,
  base_price DECIMAL(12,2) NOT NULL,
  sale_price DECIMAL(12,2) NULL,
  currency CHAR(3) NOT NULL DEFAULT 'INR',
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  featured BOOLEAN NOT NULL DEFAULT FALSE,
  seo_title VARCHAR(160) NULL,
  seo_description VARCHAR(320) NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT uq_products_sku UNIQUE (sku),
  CONSTRAINT uq_products_slug UNIQUE (slug),
  CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories(category_id),
  CONSTRAINT ck_products_price CHECK (base_price >= 0 AND (sale_price IS NULL OR (sale_price >= 0 AND sale_price <= base_price))),
  INDEX ix_products_status_category (status, category_id),
  INDEX ix_products_status_price (status, base_price),
  INDEX ix_products_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE product_attribute_values (
  product_id BIGINT NOT NULL,
  attribute_code VARCHAR(50) NOT NULL,
  value_code VARCHAR(50) NULL,
  text_value VARCHAR(200) NULL,
  PRIMARY KEY (product_id, attribute_code),
  CONSTRAINT fk_pav_product FOREIGN KEY (product_id) REFERENCES products(product_id),
  CONSTRAINT fk_pav_attr FOREIGN KEY (attribute_code) REFERENCES product_attribute_definitions(code),
  INDEX ix_pav_filter (attribute_code, value_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE product_images (
  image_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  product_id BIGINT NOT NULL,
  storage_key VARCHAR(80) NOT NULL,
  alt_text VARCHAR(250) NOT NULL,
  sort_order INT NOT NULL,
  width INT NOT NULL,
  height INT NOT NULL,
  is_primary BOOLEAN NOT NULL DEFAULT FALSE,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  CONSTRAINT fk_pi_product FOREIGN KEY (product_id) REFERENCES products(product_id),
  INDEX ix_pi_order (product_id, sort_order),
  CONSTRAINT ck_pi_order CHECK (sort_order BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE inventory (
  product_id BIGINT NOT NULL PRIMARY KEY,
  on_hand INT NOT NULL DEFAULT 0,
  reserved INT NOT NULL DEFAULT 0,
  low_stock_threshold INT NOT NULL DEFAULT 5,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT fk_inv_product FOREIGN KEY (product_id) REFERENCES products(product_id),
  CONSTRAINT ck_inv_nonneg CHECK (on_hand >= 0 AND reserved >= 0 AND reserved <= on_hand)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE inventory_movements (
  movement_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  product_id BIGINT NOT NULL,
  quantity_delta INT NOT NULL,
  reason VARCHAR(250) NOT NULL,
  reference_type VARCHAR(30) NULL,
  reference_id VARCHAR(60) NULL,
  actor VARCHAR(100) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  CONSTRAINT fk_im_product FOREIGN KEY (product_id) REFERENCES products(product_id),
  INDEX ix_im_product (product_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE carts (
  cart_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT uq_carts_user UNIQUE (user_id),
  CONSTRAINT fk_carts_user FOREIGN KEY (user_id) REFERENCES app_users(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE cart_items (
  cart_item_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  cart_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  quantity INT NOT NULL,
  added_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  CONSTRAINT uq_cart_items UNIQUE (cart_id, product_id),
  CONSTRAINT fk_ci_cart FOREIGN KEY (cart_id) REFERENCES carts(cart_id),
  CONSTRAINT fk_ci_product FOREIGN KEY (product_id) REFERENCES products(product_id),
  CONSTRAINT ck_ci_qty CHECK (quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE wishlists (
  wishlist_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  created_at DATETIME(6) NOT NULL,
  CONSTRAINT uq_wishlists_user UNIQUE (user_id),
  CONSTRAINT fk_wl_user FOREIGN KEY (user_id) REFERENCES app_users(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE wishlist_items (
  wishlist_item_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  wishlist_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  added_at DATETIME(6) NOT NULL,
  CONSTRAINT uq_wishlist_items UNIQUE (wishlist_id, product_id),
  CONSTRAINT fk_wli_wl FOREIGN KEY (wishlist_id) REFERENCES wishlists(wishlist_id),
  CONSTRAINT fk_wli_product FOREIGN KEY (product_id) REFERENCES products(product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE customer_orders (
  order_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  order_number VARCHAR(30) NOT NULL,
  user_id BIGINT NOT NULL,
  status VARCHAR(30) NOT NULL,
  payment_method VARCHAR(20) NOT NULL,
  payment_status VARCHAR(30) NOT NULL,
  subtotal DECIMAL(12,2) NOT NULL,
  shipping_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
  tax_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
  discount_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
  total DECIMAL(12,2) NOT NULL,
  currency CHAR(3) NOT NULL,
  ship_recipient VARCHAR(150) NOT NULL,
  ship_phone VARCHAR(20) NOT NULL,
  ship_line1 VARCHAR(200) NOT NULL,
  ship_line2 VARCHAR(200) NULL,
  ship_locality VARCHAR(120) NULL,
  ship_city VARCHAR(100) NOT NULL,
  ship_state VARCHAR(100) NOT NULL,
  ship_postal_code VARCHAR(12) NOT NULL,
  ship_country VARCHAR(60) NOT NULL,
  idempotency_key VARCHAR(80) NOT NULL,
  packing_notes VARCHAR(1000) NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT uq_orders_number UNIQUE (order_number),
  CONSTRAINT uq_orders_idem UNIQUE (user_id, idempotency_key),
  CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES app_users(user_id),
  INDEX ix_orders_user_created (user_id, created_at),
  INDEX ix_orders_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE order_items (
  order_item_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  product_id BIGINT NULL,
  sku VARCHAR(64) NOT NULL,
  title VARCHAR(200) NOT NULL,
  image_key VARCHAR(80) NULL,
  unit_price DECIMAL(12,2) NOT NULL,
  quantity INT NOT NULL,
  line_total DECIMAL(12,2) NOT NULL,
  CONSTRAINT fk_oi_order FOREIGN KEY (order_id) REFERENCES customer_orders(order_id),
  CONSTRAINT fk_oi_product FOREIGN KEY (product_id) REFERENCES products(product_id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE order_status_history (
  history_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  status VARCHAR(30) NOT NULL,
  note VARCHAR(500) NULL,
  actor VARCHAR(100) NOT NULL,
  changed_at DATETIME(6) NOT NULL,
  CONSTRAINT fk_osh_order FOREIGN KEY (order_id) REFERENCES customer_orders(order_id),
  INDEX ix_osh_order (order_id, changed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE payments (
  payment_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  provider VARCHAR(30) NOT NULL,
  method VARCHAR(20) NOT NULL,
  status VARCHAR(30) NOT NULL,
  amount DECIMAL(12,2) NOT NULL,
  currency CHAR(3) NOT NULL,
  provider_reference VARCHAR(80) NULL,
  idempotency_key VARCHAR(100) NOT NULL,
  response_metadata VARCHAR(1000) NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT uq_payments_idem UNIQUE (idempotency_key),
  CONSTRAINT fk_pay_order FOREIGN KEY (order_id) REFERENCES customer_orders(order_id),
  INDEX ix_pay_ref (provider_reference)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE payment_webhook_events (
  provider_code VARCHAR(30) NOT NULL,
  event_id VARCHAR(100) NOT NULL,
  received_at DATETIME(6) NOT NULL,
  PRIMARY KEY (provider_code, event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE shipments (
  shipment_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  carrier VARCHAR(80) NULL,
  tracking_number VARCHAR(80) NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
  shipped_at DATETIME(6) NULL,
  delivered_at DATETIME(6) NULL,
  ship_from_snapshot VARCHAR(600) NULL,
  tracking_note VARCHAR(500) NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  CONSTRAINT fk_ship_order FOREIGN KEY (order_id) REFERENCES customer_orders(order_id),
  INDEX ix_ship_tracking (tracking_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE product_interaction_events (
  event_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  product_id BIGINT NOT NULL,
  event_type VARCHAR(20) NOT NULL,
  session_key VARCHAR(64) NULL,
  user_id BIGINT NULL,
  dedupe_key VARCHAR(100) NULL,
  created_at DATETIME(6) NOT NULL,
  CONSTRAINT fk_pie_product FOREIGN KEY (product_id) REFERENCES products(product_id),
  INDEX ix_pie_product_type_time (product_id, event_type, created_at),
  INDEX ix_pie_time (created_at),
  INDEX ix_pie_dedupe (dedupe_key, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE shop_profiles (
  shop_id BIGINT NOT NULL PRIMARY KEY,
  display_name VARCHAR(120) NOT NULL,
  tagline VARCHAR(200) NULL,
  logo_url VARCHAR(300) NULL,
  support_email VARCHAR(254) NULL,
  support_phone VARCHAR(30) NULL,
  sender_display_name VARCHAR(120) NULL,
  address_line1 VARCHAR(200) NULL,
  address_line2 VARCHAR(200) NULL,
  city VARCHAR(100) NULL,
  state VARCHAR(100) NULL,
  postal_code VARCHAR(12) NULL,
  country VARCHAR(60) NULL,
  return_address VARCHAR(500) NULL,
  handling_time_days INT NOT NULL DEFAULT 2,
  updated_at DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE site_settings (
  setting_key VARCHAR(80) NOT NULL PRIMARY KEY,
  setting_value VARCHAR(2000) NOT NULL,
  group_code VARCHAR(40) NOT NULL,
  value_type VARCHAR(20) NOT NULL,
  description VARCHAR(250) NULL,
  options VARCHAR(250) NULL,
  updated_by VARCHAR(100) NULL,
  updated_at DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE site_links (
  link_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  placement VARCHAR(10) NOT NULL,
  label VARCHAR(60) NOT NULL,
  url VARCHAR(300) NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  active BOOLEAN NOT NULL DEFAULT TRUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE content_pages (
  page_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  slug VARCHAR(80) NOT NULL,
  title VARCHAR(160) NOT NULL,
  published BOOLEAN NOT NULL DEFAULT FALSE,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  CONSTRAINT uq_content_pages_slug UNIQUE (slug)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE content_blocks (
  block_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  page_id BIGINT NOT NULL,
  block_type VARCHAR(30) NOT NULL,
  title VARCHAR(200) NULL,
  subtitle VARCHAR(300) NULL,
  body TEXT NULL,
  image_url VARCHAR(300) NULL,
  link_url VARCHAR(300) NULL,
  cta_label VARCHAR(60) NULL,
  product_rule VARCHAR(20) NULL,
  category_id BIGINT NULL,
  item_limit INT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  published BOOLEAN NOT NULL DEFAULT TRUE,
  CONSTRAINT fk_cb_page FOREIGN KEY (page_id) REFERENCES content_pages(page_id),
  CONSTRAINT fk_cb_category FOREIGN KEY (category_id) REFERENCES categories(category_id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bulk_import_jobs (
  job_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  mode VARCHAR(10) NOT NULL,
  allow_partial BOOLEAN NOT NULL DEFAULT FALSE,
  filename VARCHAR(200) NOT NULL,
  checksum VARCHAR(64) NOT NULL,
  status VARCHAR(20) NOT NULL,
  total_rows INT NOT NULL DEFAULT 0,
  valid_rows INT NOT NULL DEFAULT 0,
  invalid_rows INT NOT NULL DEFAULT 0,
  committed_rows INT NOT NULL DEFAULT 0,
  has_images BOOLEAN NOT NULL DEFAULT FALSE,
  created_by VARCHAR(100) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bulk_import_rows (
  row_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  job_id BIGINT NOT NULL,
  row_num INT NOT NULL,
  sku VARCHAR(64) NULL,
  payload_json TEXT NOT NULL,
  valid BOOLEAN NOT NULL,
  result VARCHAR(20) NOT NULL,
  errors_json TEXT NULL,
  product_id BIGINT NULL,
  CONSTRAINT fk_bir_job FOREIGN KEY (job_id) REFERENCES bulk_import_jobs(job_id),
  INDEX ix_bir_job (job_id, row_num)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE email_outbox (
  outbox_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  message_type VARCHAR(40) NOT NULL,
  recipient VARCHAR(254) NOT NULL,
  subject VARCHAR(250) NOT NULL,
  body TEXT NOT NULL,
  ref_type VARCHAR(30) NULL,
  ref_id BIGINT NULL,
  state VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  attempts INT NOT NULL DEFAULT 0,
  next_attempt_at DATETIME(6) NOT NULL,
  error_summary VARCHAR(300) NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  INDEX ix_outbox_due (state, next_attempt_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE application_error_events (
  event_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  signature VARCHAR(64) NOT NULL,
  correlation_id VARCHAR(64) NOT NULL,
  severity VARCHAR(10) NOT NULL,
  occurred_at DATETIME(6) NOT NULL,
  last_occurred_at DATETIME(6) NOT NULL,
  occurrence_count INT NOT NULL DEFAULT 1,
  operation VARCHAR(150) NOT NULL,
  route VARCHAR(250) NOT NULL,
  error_type VARCHAR(200) NOT NULL,
  summary VARCHAR(400) NOT NULL,
  environment VARCHAR(30) NOT NULL,
  notification_state VARCHAR(20) NOT NULL DEFAULT 'NONE',
  remediation_state VARCHAR(20) NOT NULL DEFAULT 'OPEN',
  INDEX ix_err_sig (signature, last_occurred_at),
  INDEX ix_err_sev_time (severity, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE admin_audit_logs (
  audit_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  actor VARCHAR(100) NOT NULL,
  action VARCHAR(60) NOT NULL,
  target_type VARCHAR(40) NOT NULL,
  target_id VARCHAR(60) NOT NULL,
  summary VARCHAR(500) NOT NULL,
  outcome VARCHAR(20) NOT NULL,
  correlation_id VARCHAR(64) NULL,
  created_at DATETIME(6) NOT NULL,
  INDEX ix_audit_time (created_at),
  INDEX ix_audit_action (action, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
