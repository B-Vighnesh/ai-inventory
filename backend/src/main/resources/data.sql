INSERT INTO product (id, sku, name, category, current_price, stock_level, reorder_threshold, demand_velocity, status) VALUES ('PRD-001', 'SKU-ELEC-001', 'Wireless Earbuds Pro', 'ELECTRONICS', 79.99, 45, 20, 3, 'ACTIVE');
INSERT INTO product (id, sku, name, category, current_price, stock_level, reorder_threshold, demand_velocity, status) VALUES ('PRD-002', 'SKU-ELEC-002', 'USB-C Hub 7-Port', 'ELECTRONICS', 34.99, 120, 30, 1, 'ACTIVE');
INSERT INTO product (id, sku, name, category, current_price, stock_level, reorder_threshold, demand_velocity, status) VALUES ('PRD-003', 'SKU-APP-001', 'Organic Cotton T-Shirt', 'APPAREL', 24.99, 8, 15, 12, 'PRICE_REVIEW_PENDING');
INSERT INTO product (id, sku, name, category, current_price, stock_level, reorder_threshold, demand_velocity, status) VALUES ('PRD-004', 'SKU-APP-002', 'Running Shorts - Navy', 'APPAREL', 39.99, 55, 20, 2, 'ACTIVE');
INSERT INTO product (id, sku, name, category, current_price, stock_level, reorder_threshold, demand_velocity, status) VALUES ('PRD-005', 'SKU-HOME-001', 'Ceramic Pour-Over Set', 'HOME', 49.99, 22, 10, 4, 'ACTIVE');
INSERT INTO product (id, sku, name, category, current_price, stock_level, reorder_threshold, demand_velocity, status) VALUES ('PRD-006', 'SKU-HOME-002', 'LED Desk Lamp - Dimmable', 'HOME', 59.99, 0, 15, 0, 'OUT_OF_STOCK');
INSERT INTO product (id, sku, name, category, current_price, stock_level, reorder_threshold, demand_velocity, status) VALUES ('PRD-007', 'SKU-ELEC-003', 'Portable Charger 20K', 'ELECTRONICS', 44.99, 18, 25, 8, 'ACTIVE');
INSERT INTO product (id, sku, name, category, current_price, stock_level, reorder_threshold, demand_velocity, status) VALUES ('PRD-008', 'SKU-APP-003', 'Hoodie - Heather Grey', 'APPAREL', 54.99, 11, 12, 15, 'ACTIVE');

-- Seed recent sales for the rolling 24-hour demand window.
INSERT INTO sale_record (id, product_id, quantity, occurred_at) VALUES ('SEED-1', 'PRD-001', 3, CURRENT_TIMESTAMP);
INSERT INTO sale_record (id, product_id, quantity, occurred_at) VALUES ('SEED-2', 'PRD-002', 1, CURRENT_TIMESTAMP);
INSERT INTO sale_record (id, product_id, quantity, occurred_at) VALUES ('SEED-3', 'PRD-003', 12, CURRENT_TIMESTAMP);
INSERT INTO sale_record (id, product_id, quantity, occurred_at) VALUES ('SEED-4', 'PRD-004', 2, CURRENT_TIMESTAMP);
INSERT INTO sale_record (id, product_id, quantity, occurred_at) VALUES ('SEED-5', 'PRD-005', 4, CURRENT_TIMESTAMP);
INSERT INTO sale_record (id, product_id, quantity, occurred_at) VALUES ('SEED-7', 'PRD-007', 8, CURRENT_TIMESTAMP);
INSERT INTO sale_record (id, product_id, quantity, occurred_at) VALUES ('SEED-8', 'PRD-008', 15, CURRENT_TIMESTAMP);
INSERT INTO inventory_snapshot (id, product_id, stock_level, demand_velocity, current_price, reason, captured_at) SELECT id, id, stock_level, demand_velocity, current_price, 'INITIAL', CURRENT_TIMESTAMP FROM product;
