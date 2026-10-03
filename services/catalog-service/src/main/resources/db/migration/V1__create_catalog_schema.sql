CREATE TABLE categories (
    id UUID NOT NULL,
    name VARCHAR(160) NOT NULL,
    slug VARCHAR(180) NOT NULL,
    description VARCHAR(1000),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_categories PRIMARY KEY (id),
    CONSTRAINT uq_categories_slug UNIQUE (slug),
    CONSTRAINT ck_categories_name_not_blank CHECK (LENGTH(TRIM(name)) > 0),
    CONSTRAINT ck_categories_slug_not_blank CHECK (LENGTH(TRIM(slug)) > 0)
);

CREATE TABLE products (
    id UUID NOT NULL,
    category_id UUID NOT NULL,
    sku VARCHAR(64) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(2000),
    price_amount NUMERIC(12, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_products PRIMARY KEY (id),
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT uq_products_sku UNIQUE (sku),
    CONSTRAINT ck_products_sku_not_blank CHECK (LENGTH(TRIM(sku)) > 0),
    CONSTRAINT ck_products_name_not_blank CHECK (LENGTH(TRIM(name)) > 0),
    CONSTRAINT ck_products_price_non_negative CHECK (price_amount >= 0),
    CONSTRAINT ck_products_currency_length CHECK (LENGTH(currency) = 3),
    CONSTRAINT ck_products_status CHECK (status IN ('DRAFT', 'ACTIVE', 'INACTIVE'))
);

CREATE TABLE product_attributes (
    id UUID NOT NULL,
    product_id UUID NOT NULL,
    attribute_key VARCHAR(120) NOT NULL,
    attribute_value VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_product_attributes PRIMARY KEY (id),
    CONSTRAINT fk_product_attributes_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
    CONSTRAINT uq_product_attributes_product_key UNIQUE (product_id, attribute_key),
    CONSTRAINT ck_product_attributes_key_not_blank CHECK (LENGTH(TRIM(attribute_key)) > 0),
    CONSTRAINT ck_product_attributes_value_not_blank CHECK (LENGTH(TRIM(attribute_value)) > 0)
);

CREATE INDEX idx_categories_active ON categories (active);
CREATE INDEX idx_products_category_id ON products (category_id);
CREATE INDEX idx_products_status ON products (status);
CREATE INDEX idx_products_category_status ON products (category_id, status);
CREATE INDEX idx_product_attributes_product_id ON product_attributes (product_id);
