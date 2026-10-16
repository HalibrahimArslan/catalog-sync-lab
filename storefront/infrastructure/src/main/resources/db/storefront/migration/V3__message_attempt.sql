-- Teslimat geçmişi (mesaj takibi): her mesajın her denemesi, sonucu ve hatası. Celery'nin result
-- backend'inin karşılığı. Gözlem amaçlı: katalog transaction'ının parçası değil; başarısız deneme de
-- yazılsın diye ayrı yazılır. Inbox kayıtlarıyla aynı saklama süresinden sonra silinir.
CREATE TABLE message_attempt (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, -- yazılış sırası
    message_id     TEXT,                                            -- kimliksiz (bozuk) mesajda null
    queue          TEXT        NOT NULL,
    message_type   TEXT,
    sku            TEXT,                                            -- gövdeden okunabildiyse
    schema_version INTEGER,
    attempt        INTEGER     NOT NULL,                            -- yeniden oynatmada 0
    outcome        TEXT        NOT NULL CHECK (outcome IN ('PROCESSED', 'DUPLICATE', 'RETRY', 'PARKED', 'REPLAYED')),
    error          TEXT,
    at             TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX message_attempt_message_id ON message_attempt (message_id);
CREATE INDEX message_attempt_sku ON message_attempt (sku);
CREATE INDEX message_attempt_at ON message_attempt (at);
