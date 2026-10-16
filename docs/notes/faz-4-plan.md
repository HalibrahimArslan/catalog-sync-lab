# Faz 4 — Plan: İşletim

Mimari Faz 3'teki gibi (Onion, halka başına modül). Bu faz, sistemi **çalıştıran** insanın sorunlarını
çözüyor: Faz 2 ve 3'ten kalan acıtan noktalar 6, 7 ve 8. Çalışma şekli aynı: `test:` (kırmızı) →
`feat:` (yeşil), sonunda `p4-mN` tag'i.

**Durum:** M1–M4 tamamlandı. İnceleme için: [faz-4-inceleme.md](faz-4-inceleme.md)

## Kararlar
| Konu | Seçim | Gerekçe | Alternatif |
|---|---|---|---|
| Polling gecikmesi (8) | PostgreSQL `LISTEN/NOTIFY`: outbox'a satır eklenince trigger bildirim gönderir, relay anında uyanır. Polling yedek olarak kalır (5 sn) | Yeni altyapı yok; bildirim transaction commit olunca gider, yani relay yarım işi görmez | CDC (Debezium): daha güçlü ama ayrı bir platform |
| Park kuyruğu (6) | Storefront'a yönetim uç noktaları: park edilenleri listele, ana kuyruğa geri gönder. Geri gönderilen mesajın deneme sayacı sıfırlanır | Sorunu düzelten insan (ör. eksik ürünü Backoffice'te oluşturan) mesajı tekrar işletebilmeli | RabbitMQ Shovel eklentisi: sayaçları sıfırlamaz, x-death birikir |
| Sözleşme sürümü (7) | Sürüm AMQP başlığında (`x-schema-version`). Örnek kırıcı değişiklik: `price` + `currency` → `price: {amount, currency}`. Storefront iki sürümü okur; Backoffice'te yayınlanan sürüm ayarla seçilir | Kırıcı değişikliğin güvenli sırası: önce tüketici iki sürümü okur, sonra üretici yeni sürüme geçer, en son eski sürüm emekliye ayrılır | Sürümü tip adına gömmek (`ProductCreated.v2`): routing ve tip eşlemesi karmaşıklaşır |

## Milestone'lar
| # | İçerik | Acıtan nokta |
|---|---|---|
| M1 | Outbox trigger + `LISTEN/NOTIFY`; relay bildirimle uyanır, polling yedek | 8 |
| M2 | Park kuyruğu yönetimi: listele, yeniden oynat (sayaç sıfırlanır), yönetim REST uç noktaları | 6 |
| M3 | Sözleşme sürümü: v2 mesajlar, Storefront iki sürümü okur, Backoffice'te sürüm ayarı, outbox satırı kendi sürümünü taşır | 7 |
| M4 | Kabul senaryoları (park → düzelt → yeniden oynat; sürüm geçişi) + Faz 4 inceleme rehberi | — |

## Kapsam dışı (bilinçli)
- Yönetim uç noktalarında kimlik doğrulama (gerçek sistemde şart; burada not düşülür)
- Faz 3'ün acıtan noktaları (modül sayısı, okuma projeksiyonu, lider seçimi)
