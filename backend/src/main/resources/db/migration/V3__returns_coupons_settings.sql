-- V3: return requests (SRS 9.4), coupons (SRS 4.1 P1), extra settings.

CREATE TABLE return_requests (
  return_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  reason VARCHAR(40) NOT NULL,
  details VARCHAR(1000) NULL,
  status VARCHAR(20) NOT NULL,
  admin_note VARCHAR(500) NULL,
  refund_amount DECIMAL(12,2) NULL,
  decided_by VARCHAR(80) NULL,
  decided_at DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT fk_returns_order FOREIGN KEY (order_id) REFERENCES customer_orders(order_id),
  CONSTRAINT fk_returns_user FOREIGN KEY (user_id) REFERENCES app_users(user_id),
  INDEX ix_returns_order (order_id),
  INDEX ix_returns_user_created (user_id, created_at),
  INDEX ix_returns_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE coupons (
  coupon_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(40) NOT NULL,
  description VARCHAR(200) NULL,
  discount_type VARCHAR(10) NOT NULL,
  discount_value DECIMAL(12,2) NOT NULL,
  min_subtotal DECIMAL(12,2) NOT NULL DEFAULT 0,
  max_discount DECIMAL(12,2) NULL,
  starts_at DATETIME(6) NULL,
  ends_at DATETIME(6) NULL,
  usage_limit INT NULL,
  per_user_limit INT NULL,
  times_used INT NOT NULL DEFAULT 0,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT uq_coupons_code UNIQUE (code),
  CONSTRAINT ck_coupons_type CHECK (discount_type IN ('PERCENT','FIXED')),
  CONSTRAINT ck_coupons_value CHECK (discount_value > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE coupon_redemptions (
  redemption_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  coupon_id BIGINT NOT NULL,
  order_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  discount_amount DECIMAL(12,2) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  CONSTRAINT uq_redemption_order UNIQUE (order_id),
  CONSTRAINT fk_redemption_coupon FOREIGN KEY (coupon_id) REFERENCES coupons(coupon_id),
  CONSTRAINT fk_redemption_order FOREIGN KEY (order_id) REFERENCES customer_orders(order_id),
  CONSTRAINT fk_redemption_user FOREIGN KEY (user_id) REFERENCES app_users(user_id),
  INDEX ix_redemption_coupon_user (coupon_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE customer_orders ADD COLUMN coupon_code VARCHAR(40) NULL AFTER discount_amount;

INSERT INTO site_settings (setting_key, setting_value, group_code, value_type, description, options, updated_at) VALUES
 ('media.maxFileMb','5','MEDIA','INTEGER','Maximum size of one uploaded product image, in MB (1-20)',NULL,UTC_TIMESTAMP(6)),
 ('returns.enabled','true','BUSINESS','BOOLEAN','Allow customers to request returns on delivered orders',NULL,UTC_TIMESTAMP(6)),
 ('returns.windowDays','7','BUSINESS','INTEGER','Days after delivery during which a return can be requested',NULL,UTC_TIMESTAMP(6)),
 ('coupons.enabled','true','BUSINESS','BOOLEAN','Allow customers to enter coupon codes at checkout',NULL,UTC_TIMESTAMP(6)),
 ('alerts.lowStockEmail','true','INVENTORY','BOOLEAN','Email admins when a product reaches its low-stock threshold',NULL,UTC_TIMESTAMP(6));
