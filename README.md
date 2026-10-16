# catalog-sync-lab

[![build](https://github.com/HalibrahimArslan/catalog-sync-lab/actions/workflows/build.yml/badge.svg)](https://github.com/HalibrahimArslan/catalog-sync-lab/actions/workflows/build.yml)

Backoffice → E-ticaret (Storefront) ürün ve stok senkronizasyonu üzerinden
RabbitMQ, DDD, Hexagonal ve Onion mimarisini TDD ile öğrenme projesi.

| Branch | İçerik |
|---|---|
| `main` | Senaryo, sözlük, docker-compose |
| `phase-1-basics` | Faz 1 — saf `amqp-client` ile RabbitMQ temelleri |
| `phase-2-hexagonal` | Faz 2 — DDD + Hexagonal, Spring Boot, PostgreSQL, Outbox, Cucumber |
| `phase-3-onion` | Faz 3 — Onion: halka başına Maven modülü, okuma modeli, outbox/inbox temizliği |
| `phase-4-operations` | Faz 4 — İşletim: LISTEN/NOTIFY, park kuyruğu yönetimi, sözleşme sürümü |

- Senaryo: [docs/scenario.md](docs/scenario.md)
- Sözlük: [docs/glossary.md](docs/glossary.md)
- Faz 2 planı: [docs/notes/faz-2-plan.md](docs/notes/faz-2-plan.md)
- Faz 2 inceleme rehberi: [docs/notes/faz-2-inceleme.md](docs/notes/faz-2-inceleme.md)
- Faz 3 planı: [docs/notes/faz-3-plan.md](docs/notes/faz-3-plan.md)
- Faz 3 inceleme rehberi: [docs/notes/faz-3-inceleme.md](docs/notes/faz-3-inceleme.md)
- Faz 4 planı: [docs/notes/faz-4-plan.md](docs/notes/faz-4-plan.md)
- Faz 4 inceleme rehberi: [docs/notes/faz-4-inceleme.md](docs/notes/faz-4-inceleme.md)

## Gereksinimler
- Java 21
- Docker (OrbStack) — testler Testcontainers ile gerçek RabbitMQ ve PostgreSQL'e karşı koşar
- Maven kurulu olmak zorunda değil: `./mvnw verify`

## Modüller
| Modül | İçerik |
|---|---|
| `contract` | Backoffice → Storefront mesaj sözleşmesi (bağımlılıksız) |
| `backoffice/{model,domain-service,application,infrastructure}` | Ürün ve stoğun asıl kaydı; outbox ile yayın (port 8081) |
| `storefront/{model,domain-service,application,infrastructure}` | Vitrin; event'leri tüketip kalıcı kataloğa uygular (port 8082) |
| `acceptance` | US1–US7 Cucumber senaryoları; iki uygulama birlikte, uçtan uca |

## Yerel altyapı
```bash
docker compose up -d
# RabbitMQ: http://localhost:15672  rabbitlab / rabbitlab
# PostgreSQL: localhost:5432  rabbitlab / rabbitlab  (veritabanları: backoffice, storefront)
```

## Testler
```bash
./mvnw verify
# Kabul testi raporu: acceptance/target/cucumber-report.html
```
GitHub Actions her push ve PR'da aynı komutu çalıştırır (`.github/workflows/build.yml`); kabul testi raporu
çalıştırmanın artifact'i olarak indirilebilir.

## Uygulamaları çalıştırmak
```bash
docker compose up -d
./mvnw -q package -DskipTests
java -jar backoffice/infrastructure/target/backoffice-0.4.0-SNAPSHOT-exec.jar &
java -jar storefront/infrastructure/target/storefront-0.4.0-SNAPSHOT-exec.jar &

curl -X POST localhost:8081/products -H 'Content-Type: application/json' \
     -d '{"sku":"SKU-1","name":"Kupa","description":"Seramik kupa","price":100,"currency":"TRY"}'
curl -X PUT localhost:8081/products/SKU-1/stock -H 'Content-Type: application/json' -d '{"quantity":8}'
curl localhost:8082/catalog

# Park kuyruğu (P4-M2): park edilenleri gör, sorunu düzeltince yeniden oynat
curl localhost:8082/admin/parking/storefront.stock
curl -X POST localhost:8082/admin/parking/storefront.stock/replay
```

Sözleşme sürümü (P4-M3): Backoffice varsayılan olarak v1 yayınlar; v2'ye geçmek için
`--backoffice.outbox.schema-version=2`. Storefront iki sürümü de okur.
