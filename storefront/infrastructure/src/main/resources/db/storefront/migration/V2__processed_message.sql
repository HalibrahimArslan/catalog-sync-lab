-- Inbox: işlenmiş mesajların kimlikleri. Katalog güncellemesiyle AYNI transaction'da yazılır;
-- güncelleme geri alınırsa kayıt da geri alınır ve mesaj tekrar denenebilir.
CREATE TABLE processed_message (
    message_id   TEXT PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
