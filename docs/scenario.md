# Senaryo: Backoffice → E-ticaret Ürün ve Stok Senkronizasyonu

## Özet
Backoffice uygulamasında ürünler ve stoklar yönetilir. Değişiklikler RabbitMQ üzerinden
e-ticaret vitrinine (Storefront) tek yönlü aktarılır. İki sistem anlık değil, kısa gecikmeyle
aynı duruma gelir (eventual consistency).

## Kararlar
| Karar | Seçim | Gerekçe |
|---|---|---|
| Yön | Tek yön: Backoffice → Storefront | Basitlik; tek source of truth |
| Veri sahibi | Ürün bilgisi ve stok: Backoffice | Her verinin tek sahibi olur |
| Stok mesajı | Mutlak değer + versiyon | Tekrar gelen mesaj zararsız; eski mesaj yok sayılır |
| Versiyonlar | Ürün bilgisi ve stok için **iki ayrı** versiyon sayacı | Tek sayaç olsaydı, bir fiyat güncellemesi (v3) kendisinden önce çıkmış ama geç gelen stok mesajını (v2) "eski" sayıp yutardı |
| İlk stok | `ProductCreated` stok taşımaz; ürün "Tükendi" doğar, stok `StockUpdated` ile gelir | Stok tek bir yoldan değişir, kural tek yerde kalır |
| Silme | Yok; ürün pasife alınır | Geç gelen mesajlar "kayıp ürün" sorunu yaratmaz, geçmiş korunur |

## Kapsam dışı (bilinçli)
- E-ticaretteki satışların stoku düşürmesi (sipariş entegrasyonu)
- Pasif ürünü tekrar aktifleştirme
- Kategori, görsel, çoklu para birimi

## Bounded Context'ler
- **Backoffice:** Ürünün asıl kaydı; kurallar burada.
- **Storefront:** Vitrindeki kopya; sadece gelen bilgiyi yansıtır.

## Event Storming
| # | Aktör | Komut | Event | Storefront tepkisi |
|---|---|---|---|---|
| 1 | Ürün yöneticisi | Ürün oluştur | ProductCreated | Kataloğa ekle (stok 0) |
| 2 | Ürün yöneticisi | Ürün bilgisini güncelle | ProductUpdated | Ad, açıklama, fiyatı güncelle |
| 3 | Depo görevlisi | Stoku güncelle | StockUpdated | Stoku güncelle; 0 ise "Tükendi" |
| 4 | Ürün yöneticisi | Ürünü satıştan kaldır | ProductDeactivated | Siteden gizle |

## Kurallar
- SKU benzersizdir.
- Fiyat > 0.
- Stok >= 0.
- Ürün bilgisi event'leri (`ProductCreated`, `ProductUpdated`, `ProductDeactivated`) ortak bir versiyon taşır; `StockUpdated` kendi stok versiyonunu taşır. Eski/eşit versiyon yok sayılır.
- Bilinmeyen ürüne mesaj gelirse: gecikmeli tekrar dene, yine yoksa DLQ.
- Geçersiz mesaj (ör. negatif stok): tekrar denenmez, doğrudan DLQ.

## User Story'ler ve Kabul Kriterleri

### US1 — Yeni ürün vitrine yansır
Ürün yöneticisi olarak, backoffice'te oluşturduğum ürünün e-ticarette görünmesini istiyorum.
- **Given** Storefront'ta SKU-1 yok **When** ProductCreated(SKU-1, "Kupa", 100 TRY) gelir **Then** katalogda SKU-1 aktif ve "Tükendi" olarak görünür.
- **Given** SKU-1 katalogda var **When** aynı ProductCreated tekrar gelir **Then** ikinci kayıt oluşmaz.

### US2 — Ürün bilgisi güncellemesi yansır
- **Given** SKU-1 versiyon 1 **When** ProductUpdated(SKU-1, fiyat 120, versiyon 2) gelir **Then** fiyat 120 olur.
- **Given** SKU-1 versiyon 3 **When** ProductUpdated(versiyon 2) gelir **Then** değişiklik yok sayılır.

### US3 — Stok güncellemesi yansır
Depo görevlisi olarak, saydığım stoğun e-ticarette doğru görünmesini istiyorum.
- **Given** SKU-1 stok 5 (stok v1) **When** StockUpdated(SKU-1, 8, v2) gelir **Then** stok 8 olur.
- **Given** SKU-1 stok 8 (v2) **When** aynı mesaj tekrar gelir **Then** stok 8 kalır.
- **Given** SKU-1 stok 8 (v3) **When** StockUpdated(10, v2) geç gelir **Then** stok 8 kalır.
- **Given** SKU-1 stok 1 **When** StockUpdated(0) gelir **Then** ürün "Tükendi" görünür.

### US4 — Pasife alınan ürün vitrinden kalkar
- **Given** SKU-1 aktif **When** ProductDeactivated(SKU-1) gelir **Then** ürün sitede görünmez.
- **Given** SKU-1 pasif **When** geç bir StockUpdated gelir **Then** ürün pasif kalır.

### US5 — Hatalı mesajlar sistemi kilitlemez
- **Given** Storefront kapalı **When** Backoffice 3 güncelleme gönderir **Then** Storefront açılınca hepsi işlenir, hiçbiri kaybolmaz.
- **Given** SKU-9 katalogda yok **When** StockUpdated(SKU-9) gelir **Then** mesaj gecikmeli tekrar denenir; 3 denemeden sonra DLQ'ya düşer.
- **Given** herhangi bir durum **When** StockUpdated(stok -5) gelir **Then** mesaj tekrar denenmeden DLQ'ya düşer, diğer mesajlar işlenmeye devam eder.

### US6 — Park edilen mesaj sorun düzeltilince yeniden işlenir
Sistemi işleten kişi olarak, park edilen mesajı görmek ve sorunu düzelttikten sonra tekrar işletmek istiyorum.
- **Given** SKU-9 için StockUpdated 3 denemeden sonra park edildi **When** park kuyruğu listelenir **Then** mesaj, hata sebebi ve deneme sayısıyla görünür; kuyrukta kalır.
- **Given** aynı mesaj park edilmiş **When** SKU-9 Backoffice'te oluşturulur ve mesaj yeniden oynatılır **Then** stok vitrine yansır, mesaj park kuyruğundan çıkar.
- **Given** yeniden oynatılan mesaj yine başarısız **Then** deneme sayacı sıfırdan başlar (3 yeni deneme).

### US7 — Mesaj sözleşmesi kesintisiz değişir
- **Given** vitrin v1 ve v2'yi okuyor **When** Backoffice v2 yayınlamaya başlar **Then** değişiklikler vitrine yansımaya devam eder.
- **Given** vitrin kapalı, kuyrukta v1 mesajlar bekliyor **When** Backoffice v2'ye geçer ve vitrin açılır **Then** v1 ve v2 mesajların hepsi işlenir.
- **Given** herhangi bir durum **When** vitrinin bilmediği bir sürümde mesaj gelir **Then** tekrar denenmeden DLQ'ya düşer (vitrin güncellenince US6 ile yeniden oynatılır).

## Faz Eşlemesi
| Milestone | Senaryodaki karşılığı |
|---|---|
| P1-M1 Hello Queue | ProductCreated tek kuyruğa gönderilir, Storefront tüketir; FIFO |
| P1-M2 Work Queue | StockUpdated'ı 2 Storefront instance paylaşır; consumer çökerse mesaj geri döner; sıra bozulsa da versiyon doğru sonucu verir |
| P1-M3 Exchange | Topic exchange `backoffice.events`; routing key'ler `product.created`, `product.updated`, `product.deactivated`, `stock.updated`; yeni dinleyici publisher'a dokunmadan eklenir |
| P1-M4 Güvenilirlik | Broker restart'ta stok mesajı kaybolmaz; publisher confirm; Storefront kapalıyken mesajlar birikir (US5-1) |
| P1-M5 Hata | Bilinmeyen ürün → TTL+DLX retry → DLQ (US5-2); negatif stok → doğrudan DLQ (US5-3) |
| Faz 2 | Backoffice: `Product` aggregate + outbox; Storefront: `CatalogItem` + idempotent consumer; US1-US5 → Cucumber |
| Faz 3 | Aynı davranış Onion katmanlarıyla |
| Faz 4 | İşletim: LISTEN/NOTIFY ile anında yayın; park kuyruğu yönetimi (US6); sözleşme sürümü (US7) |

## Faz 2 için açık soru (cevaplandı)
- Stok, `Product` aggregate'inin içinde mi olmalı yoksa ayrı bir `Inventory` aggregate'i mi? (İki ayrı versiyon sayacı bu sorunun ilk ipucu.)
- **Karar:** Ayrı `Inventory` aggregate'i. Farklı aktörler değiştirir, versiyon sayaçları ayrı, fiyat güncellemesi ile stok sayımı birbirini kilitlemez.
