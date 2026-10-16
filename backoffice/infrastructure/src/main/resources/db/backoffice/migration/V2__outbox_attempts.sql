-- Gönderilemeyen mesajın neden gönderilemediğini görelim. Relay her başarısız denemede günceller.
ALTER TABLE outbox ADD COLUMN attempts   INTEGER NOT NULL DEFAULT 0;
ALTER TABLE outbox ADD COLUMN last_error TEXT;
