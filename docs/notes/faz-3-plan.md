# Faz 3 — Plan: Onion

Aynı davranış (US1–US5), farklı mimari. Faz 2'nin acıtan noktalarını ([faz-2-inceleme.md](faz-2-inceleme.md))
Onion'ın araçlarıyla çözüyoruz. Çalışma şekli aynı: `test:` (kırmızı) → `feat:` (yeşil), sonunda `p3-mN` tag'i.
Saf yeniden yapılandırma olan milestone'larda `refactor:` commit'i var; davranış değişmediği için kırmızı aşaması yok,
güvencemiz mevcut testlerin yeşil kalması.

**Durum:** M0–M5 tamamlandı. İnceleme için: [faz-3-inceleme.md](faz-3-inceleme.md)

## Onion ile Hexagonal arasındaki fark
```
          Hexagonal (Faz 2)                                 Onion (Faz 3)
   ┌──────────────────────────────┐              ┌─────────────────────────────────────┐
   │ adapter.in    adapter.out    │              │ Infrastructure (web, DB, RabbitMQ)  │
   │     │  port.in   port.out ▲  │              │  ┌───────────────────────────────┐  │
   │     ▼     │         │      │ │              │  │ Application Services          │  │
   │  application ────────┘      │              │  │  ┌─────────────────────────┐  │  │
   │     │                        │              │  │  │ Domain Services         │  │  │
   │     ▼                        │              │  │  │ (+ repository arayüzleri)│  │  │
   │   domain                     │              │  │  │  ┌───────────────────┐  │  │  │
   └──────────────────────────────┘              │  │  │  │ Domain Model      │  │  │  │
   "içerisi ve dışarısı"; dışarıyla               │  │  │  └───────────────────┘  │  │  │
   her temas bir port üzerinden                   │  │  └─────────────────────────┘  │  │
                                                  │  └───────────────────────────────┘  │
                                                  └─────────────────────────────────────┘
                                                  "iç içe halkalar"; her halka sadece içtekileri bilir
```
- Repository arayüzleri **domain servisleri halkasında** (Faz 2'de application'ın `port.out`'undaydı): "ürünler kalıcıdır" bir iş kavramı.
- İş kuralı birden fazla aggregate'e yayılıyorsa (SKU benzersizliği, ürünle birlikte stok açmak) **domain servisi** olur.
- Inbound port yok: dış halka (web, listener) application servisini doğrudan çağırır.

## Kararlar
| Karar | Seçim | Gerekçe |
|---|---|---|
| Halka sınırları | Her halka ayrı Maven modülü | Dış halka içe bağımlı; tersi **derlenmez**. Faz 2'de aynı kuralı ArchUnit koruyordu |
| Modüller (context başına) | `model`, `domain-service`, `application`, `infrastructure` | Onion'ın dört halkası. `infrastructure` hem teknoloji hem Spring Boot başlangıcı (en dış halka) |
| Kapsam | Acıtan noktalar 1–5 ve 9 | Mimari olanlar + tablo temizliği. 6 (DLQ tekrar oynatma), 7 (sözleşme sürümü), 8 (polling gecikmesi) sonraki faza |

## Milestone'lar
| # | İçerik | Çözdüğü acıtan nokta (Faz 2) |
|---|---|---|
| M0 | Yeniden yapılandırma: her context `model` / `application` / `infrastructure` modüllerine bölünür. Kod aynı, testler aynı, hepsi yeşil | 9 |
| M1 | Domain servisleri halkası: repository arayüzleri buraya taşınır; `ProductRegistration` (SKU benzersizliği + stok açma); tutarlı domain exception hiyerarşisi | 3, 9 |
| M2 | Application servisleri: inbound port arayüzleri kalkar; Storefront'ta inbox application servisine girer, tek transaction tek yerde | 1, 2 |
| M3 | Storefront okuma modeli: vitrin sorguları domain nesnesi yerine görünüm döndürür (CQRS'in en hafif hâli) | 4 |
| M4 | Tablo temizliği: yayınlanmış outbox satırları ve eski inbox kayıtları zamanlanmış olarak silinir | 5 |
| M5 | ArchUnit `onionArchitecture()` kuralları + Faz 3 inceleme rehberi | — |
