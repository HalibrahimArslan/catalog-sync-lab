# catalog-sync-lab

Backoffice → E-ticaret (Storefront) ürün ve stok senkronizasyonu üzerinden
RabbitMQ, DDD, Hexagonal ve Onion mimarisini TDD ile öğrenme projesi.

| Branch | İçerik |
|---|---|
| `main` | Senaryo, sözlük, docker-compose |
| `phase-1-basics` | Faz 1 — saf `amqp-client` ile RabbitMQ temelleri |
| `phase-2-hexagonal` | Faz 2 — DDD + Hexagonal, Spring Boot, PostgreSQL, Outbox, Cucumber |
| `phase-3-onion` | Faz 3 — Onion (yakında) |

- Senaryo: [docs/scenario.md](docs/scenario.md)
- Sözlük: [docs/glossary.md](docs/glossary.md)
- Faz 2 planı: [docs/notes/faz-2-plan.md](docs/notes/faz-2-plan.md)
- Faz 2 inceleme rehberi: [docs/notes/faz-2-inceleme.md](docs/notes/faz-2-inceleme.md)

## Gereksinimler
- Java 21
- Docker (OrbStack) — testler Testcontainers ile gerçek RabbitMQ ve PostgreSQL'e karşı koşar
- Maven kurulu olmak zorunda değil: `./mvnw verify`

## Modüller
| Modül | İçerik |
|---|---|
| `contract` | Backoffice → Storefront mesaj sözleşmesi (bağımlılıksız) |
| `backoffice` | Ürün ve stoğun asıl kaydı; outbox ile yayın (port 8081) |
| `storefront` | Vitrin; event'leri tüketip kalıcı kataloğa uygular (port 8082) |
| `acceptance` | US1–US5 Cucumber senaryoları; iki uygulama birlikte, uçtan uca |

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

## Uygulamaları çalıştırmak
```bash
docker compose up -d
./mvnw -q package -DskipTests
java -jar backoffice/target/backoffice-0.2.0-SNAPSHOT-exec.jar &
java -jar storefront/target/storefront-0.2.0-SNAPSHOT-exec.jar &

curl -X POST localhost:8081/products -H 'Content-Type: application/json' \
     -d '{"sku":"SKU-1","name":"Kupa","description":"Seramik kupa","price":100,"currency":"TRY"}'
curl -X PUT localhost:8081/products/SKU-1/stock -H 'Content-Type: application/json' -d '{"quantity":8}'
curl localhost:8082/catalog
```
