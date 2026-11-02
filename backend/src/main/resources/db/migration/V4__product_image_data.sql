-- Product images are stored in MySQL as base64 text instead of files on disk.
-- Kept in a separate table so product listings (which read product_images) never load the large payload.
CREATE TABLE product_image_data (
  storage_key   CHAR(32)    NOT NULL PRIMARY KEY,
  image_id      BIGINT      NOT NULL,
  content_type  VARCHAR(40) NOT NULL DEFAULT 'image/jpeg',
  image_base64  LONGTEXT    NOT NULL,   -- base64 of the full-size JPEG
  thumb_base64  MEDIUMTEXT  NOT NULL,   -- base64 of the thumbnail JPEG
  created_at    DATETIME(6) NOT NULL,
  UNIQUE KEY ux_pid_image (image_id),
  CONSTRAINT fk_pid_image FOREIGN KEY (image_id) REFERENCES product_images(image_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
