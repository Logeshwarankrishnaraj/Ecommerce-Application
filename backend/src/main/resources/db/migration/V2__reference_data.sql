-- Reference data required by every environment (no demo credentials here).
INSERT INTO roles (role_code, display_name) VALUES
 ('ROLE_CUSTOMER','Customer'),('ROLE_ADMIN','Administrator'),('ROLE_SUPER_ADMIN','Super administrator');

INSERT INTO lov_groups (group_code, label, public_group) VALUES
 ('MATERIAL','Material',TRUE),('JEWELRY_TYPE','Jewelry type',TRUE),('COLOR','Color',TRUE),
 ('STONE_TYPE','Stone type',TRUE),('SIZE','Size',TRUE),('AVAILABILITY','Availability',TRUE);

INSERT INTO list_of_values (group_code, value_code, display_label, sort_order) VALUES
 ('MATERIAL','gold','Gold',1),('MATERIAL','silver','Silver',2),('MATERIAL','rose_gold','Rose gold',3),('MATERIAL','platinum','Platinum',4),
 ('JEWELRY_TYPE','ring','Ring',1),('JEWELRY_TYPE','necklace','Necklace',2),('JEWELRY_TYPE','earrings','Earrings',3),('JEWELRY_TYPE','bracelet','Bracelet',4),
 ('COLOR','yellow','Yellow',1),('COLOR','white','White',2),('COLOR','pink','Pink',3),('COLOR','green','Green',4),
 ('STONE_TYPE','diamond','Diamond',1),('STONE_TYPE','ruby','Ruby',2),('STONE_TYPE','emerald','Emerald',3),('STONE_TYPE','pearl','Pearl',4),('STONE_TYPE','none','No stone',5),
 ('SIZE','xs','XS',1),('SIZE','s','S',2),('SIZE','m','M',3),('SIZE','l','L',4),
 ('AVAILABILITY','ready','Ready to ship',1),('AVAILABILITY','made_to_order','Made to order',2);

INSERT INTO product_attribute_definitions (code, label, lov_group_code, filterable, sort_order) VALUES
 ('material','Material','MATERIAL',TRUE,1),('jewelry_type','Jewelry type','JEWELRY_TYPE',TRUE,2),
 ('color','Color','COLOR',TRUE,3),('stone_type','Stone type','STONE_TYPE',TRUE,4),('size','Size','SIZE',TRUE,5);

INSERT INTO shop_profiles (shop_id, display_name, tagline, support_email, handling_time_days, country, updated_at)
VALUES (1,'Jewel Store','Fine jewelry, thoughtfully made','support@jewelstore.local',2,'India',UTC_TIMESTAMP(6));

INSERT INTO site_settings (setting_key, setting_value, group_code, value_type, description, options, updated_at) VALUES
 ('theme.bg','#FAF6EE','THEME','COLOR','Page background (cream)',NULL,UTC_TIMESTAMP(6)),
 ('theme.surface','#FFFFFF','THEME','COLOR','Card / surface color',NULL,UTC_TIMESTAMP(6)),
 ('theme.text','#2B2622','THEME','COLOR','Body text color',NULL,UTC_TIMESTAMP(6)),
 ('theme.accent','#9C7A3C','THEME','COLOR','Accent color',NULL,UTC_TIMESTAMP(6)),
 ('theme.font','SERIF','THEME','ENUM','Typography set','SERIF,SANS,MIXED',UTC_TIMESTAMP(6)),
 ('media.aspectRatio','1:1','MEDIA','ENUM','Product image aspect ratio (applies to new uploads)','1:1,4:5,3:4',UTC_TIMESTAMP(6)),
 ('currency.code','INR','BUSINESS','TEXT','ISO currency code',NULL,UTC_TIMESTAMP(6)),
 ('shipping.flatFee','99.00','BUSINESS','DECIMAL','Flat shipping fee',NULL,UTC_TIMESTAMP(6)),
 ('shipping.freeOver','5000.00','BUSINESS','DECIMAL','Free shipping above this subtotal (0 disables)',NULL,UTC_TIMESTAMP(6)),
 ('tax.ratePercent','0','BUSINESS','DECIMAL','Tax rate percent - set only per business/legal advice',NULL,UTC_TIMESTAMP(6)),
 ('payments.cod.enabled','true','PAYMENTS','BOOLEAN','Enable Cash on Delivery',NULL,UTC_TIMESTAMP(6)),
 ('payments.online.enabled','true','PAYMENTS','BOOLEAN','Enable online payment (mock in local/test)',NULL,UTC_TIMESTAMP(6)),
 ('inventory.lowStockDefault','5','INVENTORY','INTEGER','Default low-stock threshold',NULL,UTC_TIMESTAMP(6)),
 ('cart.maxQty','10','BUSINESS','INTEGER','Max quantity per cart line',NULL,UTC_TIMESTAMP(6)),
 ('orders.terms','By placing this order you agree to the store terms, shipping and returns policy. (Business to review legal text.)','BUSINESS','TEXT','Order terms shown at checkout',NULL,UTC_TIMESTAMP(6));

INSERT INTO site_links (placement, label, url, sort_order, active) VALUES
 ('HEADER','Shop','/products',1,TRUE),('HEADER','About','/pages/about',2,TRUE),('HEADER','Contact','/pages/contact',3,TRUE),
 ('FOOTER','Shipping','/pages/shipping',1,TRUE),('FOOTER','Returns','/pages/returns',2,TRUE),
 ('FOOTER','Privacy','/pages/privacy',3,TRUE),('FOOTER','Terms','/pages/terms',4,TRUE);

INSERT INTO content_pages (slug, title, published, created_at, updated_at) VALUES
 ('home','Home',TRUE,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)),
 ('about','About us',TRUE,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)),
 ('contact','Contact',TRUE,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)),
 ('shipping','Shipping',TRUE,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)),
 ('returns','Returns',TRUE,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)),
 ('privacy','Privacy policy',TRUE,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)),
 ('terms','Terms and conditions',TRUE,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6));

INSERT INTO content_blocks (page_id, block_type, title, subtitle, body, cta_label, link_url, product_rule, item_limit, sort_order, published)
SELECT page_id,'HERO','Quiet luxury, made to last','Handcrafted jewelry in gold, silver and gemstones',NULL,'Shop the collection','/products',NULL,NULL,1,TRUE FROM content_pages WHERE slug='home';
INSERT INTO content_blocks (page_id, block_type, title, product_rule, item_limit, sort_order, published)
SELECT page_id,'CATEGORY_TILES','Shop by category',NULL,6,2,TRUE FROM content_pages WHERE slug='home';
INSERT INTO content_blocks (page_id, block_type, title, product_rule, item_limit, sort_order, published)
SELECT page_id,'FEATURED_PRODUCTS','New arrivals','NEWEST',8,3,TRUE FROM content_pages WHERE slug='home';
INSERT INTO content_blocks (page_id, block_type, title, body, sort_order, published)
SELECT page_id,'TEXT_IMAGE','Our story','Replace this text from the admin dashboard (Storefront content).',1,TRUE FROM content_pages WHERE slug='about';
INSERT INTO content_blocks (page_id, block_type, title, body, sort_order, published)
SELECT page_id,'TEXT_IMAGE','Contact us','Add your contact details from the admin dashboard (Shop profile).',1,TRUE FROM content_pages WHERE slug='contact';
INSERT INTO content_blocks (page_id, block_type, title, body, sort_order, published)
SELECT page_id,'TEXT_IMAGE','Shipping','Shipping policy text to be provided and reviewed by the business.',1,TRUE FROM content_pages WHERE slug='shipping';
INSERT INTO content_blocks (page_id, block_type, title, body, sort_order, published)
SELECT page_id,'TEXT_IMAGE','Returns','Returns policy text to be provided and reviewed by the business.',1,TRUE FROM content_pages WHERE slug='returns';
INSERT INTO content_blocks (page_id, block_type, title, body, sort_order, published)
SELECT page_id,'TEXT_IMAGE','Privacy policy','Privacy text to be provided and legally reviewed before launch.',1,TRUE FROM content_pages WHERE slug='privacy';
INSERT INTO content_blocks (page_id, block_type, title, body, sort_order, published)
SELECT page_id,'TEXT_IMAGE','Terms and conditions','Terms text to be provided and legally reviewed before launch.',1,TRUE FROM content_pages WHERE slug='terms';
