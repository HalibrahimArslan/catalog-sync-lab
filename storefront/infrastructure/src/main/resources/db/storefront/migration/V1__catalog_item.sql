-- Vitrindeki ürün kopyası. Faz 1'de bellekteydi; artık uygulama kapanınca kaybolmuyor.
CREATE TABLE catalog_item (
    sku             TEXT PRIMARY KEY,
    name            TEXT           NOT NULL,
    description     TEXT,
    price_amount    NUMERIC(12, 2) NOT NULL CHECK (price_amount > 0),
    price_currency  TEXT           NOT NULL,
    stock           INTEGER        NOT NULL CHECK (stock >= 0),
    active          BOOLEAN        NOT NULL,
    details_version BIGINT         NOT NULL, -- ürün bilgisi versiyonu
    stock_version   BIGINT         NOT NULL  -- stok versiyonu (ayrı sayaç)
);

-- Vitrin sadece aktif ürünleri listeler.
CREATE INDEX catalog_item_visible ON catalog_item (sku) WHERE active;
