-- Ürün bilgisi (Product aggregate'i).
CREATE TABLE product (
    sku            TEXT PRIMARY KEY,
    name           TEXT           NOT NULL,
    description    TEXT,
    price_amount   NUMERIC(12, 2) NOT NULL CHECK (price_amount > 0),
    price_currency TEXT           NOT NULL,
    active         BOOLEAN        NOT NULL,
    version        BIGINT         NOT NULL
);

-- Stok (Inventory aggregate'i). Ayrı tablo, ayrı versiyon: fiyat güncellemesi ile stok sayımı
-- aynı satırı kilitlemez.
CREATE TABLE inventory (
    sku      TEXT PRIMARY KEY REFERENCES product (sku),
    quantity INTEGER NOT NULL CHECK (quantity >= 0),
    version  BIGINT  NOT NULL
);

-- Giden kutusu (Transactional Outbox). Aggregate ile aynı transaction'da yazılır;
-- relay (P2-M3) yayınlanmamış satırları sırayla RabbitMQ'ya taşır.
CREATE TABLE outbox (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, -- yayın sırası
    message_id   UUID        NOT NULL UNIQUE,                     -- AMQP message-id; Storefront tekrarları bununla ayıklar
    sku          TEXT        NOT NULL,                            -- hata ayıklarken "bu ürünün mesajları" sorgusu için
    message_type TEXT        NOT NULL,
    routing_key  TEXT        NOT NULL,
    payload      JSONB       NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ
);

-- Relay sadece yayınlanmamışlara bakar; kısmi indeks onları hızlı bulur.
CREATE INDEX outbox_unpublished ON outbox (id) WHERE published_at IS NULL;
