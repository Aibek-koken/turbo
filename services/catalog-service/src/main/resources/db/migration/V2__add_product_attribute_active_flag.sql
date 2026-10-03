ALTER TABLE product_attributes
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;

CREATE INDEX idx_product_attributes_active ON product_attributes (active);
