-- Her satır yazıldığı andaki sözleşme sürümünü taşır (P4-M3). Relay x-schema-version başlığını bu sütundan
-- koyar, ayardan değil: ayar 1 → 2 değiştiğinde outbox'ta bekleyen v1 gövdeli satırlar yanlış etiketle gitmez.
-- Varsayılan 1: bu migration'dan önce yazılmış satırlar v1.
ALTER TABLE outbox ADD COLUMN schema_version INTEGER NOT NULL DEFAULT 1;
