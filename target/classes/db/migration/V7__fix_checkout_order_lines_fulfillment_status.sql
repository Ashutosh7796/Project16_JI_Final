ALTER TABLE checkout_order_lines MODIFY fulfillment_status VARCHAR(30) NOT NULL DEFAULT 'PENDING';

UPDATE checkout_order_lines SET fulfillment_status = 'PENDING' WHERE fulfillment_status = '0';
UPDATE checkout_order_lines SET fulfillment_status = 'FULFILLED' WHERE fulfillment_status = '1';
UPDATE checkout_order_lines SET fulfillment_status = 'OUT_OF_STOCK' WHERE fulfillment_status = '2';
UPDATE checkout_order_lines SET fulfillment_status = 'CANCELLED' WHERE fulfillment_status = '3';
