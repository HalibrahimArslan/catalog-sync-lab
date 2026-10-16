# Faz 3 — İnceleme Rehberi

Kodu Claude yazdı, sen inceliyorsun. Aynı düzen: `test:` (kırmızı) → `feat:` (yeşil). M0 saf yeniden
yapılandırma (`refactor:`), M5 mimari kurallar; ikisinde de kırmızı aşaması yok, güvence mevcut testlerin
yeşil kalması ve kuralların geçici ihlallerle denenmesi. **Önce testi oku, sonra kodu.**

```bash
git log --oneline phase-3-onion
git diff p2-m7 p3-m0 --stat -M          # sadece taşıma: hangi dosya hangi halkaya gitti
git diff p3-m1 p3-m2 -- storefront      # inbox'ın listener'dan servise geçişi
./mvnw verify
```

## Aynı şey, üç fazda nerede?

| Soru | Faz 1 | Faz 2 (Hexagonal) | Faz 3 (Onion) |
|---|---|---|---|
| Versiyon kontrolü (eski mesajı yok say) | `CatalogItem` (bellek) | `CatalogItem` (domain) | `CatalogItem` (model halkası) |
| SKU benzersiz mi? | kimse bakmıyor | `ProductService` (application) | `ProductRegistration` (domain-service halkası) |
| Ürünle birlikte stok açmak | yok | `ProductService` | `ProductRegistration` |
| Repository arayüzü | yok | `application.port.out` | domain-service halkası |
| Use case'e giriş | — | `*UseCase` arayüzleri (`port.in`) | application servisi, doğrudan |
| Tekrar gelen mesajı ayıklamak (inbox) | yok | listener (adapter), use case'i dıştan sarar | `CatalogService` (application), tek transaction |
| Retry / park politikası | `StorefrontConsumer` | listener (adapter) | listener (dış halka) |
| Vitrini okumak | `Catalog` (aynı nesne) | `BrowseCatalogUseCase` → `CatalogItem` | `CatalogQueries` → `CatalogView` (ayrı okuma modeli) |
| Hata türleri | birkaç `RuntimeException` | üç halkaya dağılmış | `DomainException` → InvalidValue / NotFound / RuleViolation |
| Mimariyi kim koruyor? | — | ArchUnit (paket kuralları) | **derleyici** (halka = Maven modülü) + ArchUnit (kalanlar) |

## Modüller
```
backoffice/                              storefront/
  model           Product, Inventory,      model           CatalogItem, Price, Sku,
                  Price, Sku, event'ler,                   DomainException...
                  DomainException...
  domain-service  ProductRegistration,     domain-service  CatalogRepository,
                  Product/InventoryRepo,                   UnknownProductException
                  hata sözleşmeleri
  application     ProductService,          application     CatalogService (yazma),
                  InventoryService,                        CatalogQueries/CatalogView (okuma),
                  EventOutbox, Transaction                 ProcessedUpdates, Transaction
  infrastructure  web, persistence,        infrastructure  web, messaging, persistence,
                  outbox, config, main                     config, main
```
Bir modül sadece kendinden içtekilere bağımlı. `model`'in pom'unda hiçbir bağımlılık yok.

---

## M0 — Halkalar modül oldu (`p3-m0`)
**Oku:** kök `pom.xml`, `backoffice/pom.xml`, `backoffice/*/pom.xml`
- `git diff p2-m7 p3-m0 --stat -M`: kaç dosyanın içeriği değişti? (İpucu: neredeyse hiçbiri.)
- `Product`'a `import com.rabbitlab.backoffice.application.ProductService;` ekle ve derle. Faz 2'de aynı hatayı ne yakalardı?
- `finalName` neden var?

## M1 — Domain servisleri halkası (`p3-m1`)
**Oku:** `ProductRegistrationTest` → `ProductRegistration`, `ProductRepository`, `DomainException` ve alt sınıfları, `ErrorHandler`
- SKU benzersizliği neden `Product`'ın içinde olamaz? Neden application servisinde de olmamalı?
- `ProductRegistration` neden kaydetmiyor? Kaydetseydi transaction'ı kim yönetirdi?
- Repository arayüzü neden domain-service halkasında? Faz 2'deki `port.out` yerleşimiyle farkı ne söylüyor?
- `ErrorHandler`'a yeni bir kural ihlali için satır eklemek gerekiyor mu? Faz 2'de gerekiyordu.
- `domain-service` neden test-jar yayınlıyor? Alternatifi neydi?

**Kendine sor:** Storefront'un domain-service halkası neden bu kadar ince? Bu bir sorun mu, bilgi mi?

## M2 — Inbound port'lar kalktı, transaction tek yerde (`p3-m2`)
**Oku:** `ProductControllerTest`, `CatalogServiceTest` → `ProductController`, `CatalogService`, `ProcessedUpdates`, `CatalogMessageListener`
- Inbound port'ları kaldırdık ama `EventOutbox` ve `Transaction` arayüzleri duruyor. Tutarsızlık mı? (İpucu: bağımlılık hangi yöne akmak zorunda? Application, infrastructure'ı derleme zamanında görebilir mi?)
- Listener'ın constructor'ı Faz 2'ye göre ne kadar küçüldü? Ne bilmiyor artık?
- Inbox kaydı transaction dışına alınınca hangi üç test patlıyordu, neden?
- Kimliksiz mesaj neden artık park ediliyor? Faz 2'de ne oluyordu?

## M3 — Okuma modeli (`p3-m3`)
**Oku:** `CatalogQueriesIT`, `CatalogControllerTest` → `CatalogQueries`, `CatalogView`, `JdbcCatalogQueries`
- `JdbcCatalogRepository` ile `JdbcCatalogQueries` aynı tabloyu okuyor. Farkları ne (kilit, sütunlar, dönen tip)?
- `outOfStock` kuralı iki yerde. Hangisi değişirse diğeri unutulur? Bunu nasıl önlerdin?
- Okuma için neden bir application servisi yok?
- `OnionArchitectureTest.web_never_sees_domain_model`: bu kural olmasa ne kayardı?

## M4 — Tablo temizliği (`p3-m4`)
**Oku:** `OutboxCleanupIT`, `InboxCleanupIT` → `OutboxCleaner`, `JdbcProcessedUpdates.forgetOlderThan`, `PeriodicJob`
- Neden yayınlanmış satırlar hemen silinmiyor?
- `should_stay_correct_when_forgotten_update_arrives_again`: inbox unuttu, sistem yine de doğru. Kim kurtardı?
- Inbox saklama süresini neye göre seçersin? 1 dakika olsaydı ne olurdu?
- Süre neden `now()` ile veritabanında hesaplanıyor?

## M5 — Onion kuralları (`p3-m5`)
**Oku:** `OnionArchitectureTest` (iki context)
- `git diff p3-m4 p3-m5 -- '*ArchitectureTest.java'`: Faz 2'den gelen kurallardan hangileri silindi, neden? Hangileri kaldı?
- `ignoreDependency(...config...)` neden gerekli? `config` hangi halkada?
- Testin başındaki "Hangi kuralı kim koruyor?" listesini kendi cümlelerinle anlat.

---

## Faz 2'nin acıtan noktaları → Faz 3'te ne oldu
| # | Faz 2'de | Faz 3'te | Nerede |
|---|---|---|---|
| 1 | Her use case için tek uygulamalı arayüz | Kalktı; dış halka servisi doğrudan çağırır | `ProductController`, `CatalogMessageListener` |
| 2 | Transaction ikiye bölünmüş (listener + use case) | Inbox application servisinde, tek transaction | `CatalogService.apply` |
| 3 | Exception'lar üç yerde, ortak tip yok | `DomainException` hiyerarşisi; web türleri tanır | `model/.../domain`, `ErrorHandler` |
| 4 | Domain nesnesi okuyana sızıyor | Ayrı okuma modeli + ArchUnit kuralı | `CatalogQueries`, `CatalogView` |
| 5 | Outbox ve inbox sonsuza kadar büyüyor | Saklama süreli zamanlanmış temizlik | `OutboxCleaner`, `InboxCleanupScheduler` |
| 6 | Park kuyruğu çıkmaz sokak | **Çözülmedi** (sonraki faz) | — |
| 7 | Sözleşme sürümü yok | **Çözülmedi** (sonraki faz) | — |
| 8 | Polling gecikmesi | **Çözülmedi** (sonraki faz) | — |
| 9 | Hexagon ile katmanlar arasındaki fark ince | Halkalar modül; repository domain-service'te; domain servisleri var | `*/pom.xml`, `ProductRegistration` |

---

## Bilinçli bırakılan "acıtan noktalar"
İncelerken fark et, listeye kendi bulduklarını ekle:

1. **Modül patlaması.** Kök hariç 12 Maven modülü, her biri bir pom. Derleme süresi ve IDE'de gezinme maliyeti
   arttı. Storefront'un `domain-service` halkası tek bir arayüz ve bir exception için ayrı bir modül.
   Halka başına modül her projede değer mi?
2. **Okuma modeli tam ayrı değil.** `CatalogQueries` ve `CatalogRepository` aynı tabloyu okuyor; tablo
   şeması değişirse ikisi de değişir. Gerçek ayrılık ayrı bir okuma tablosu (projeksiyon) ister.
3. **Backoffice'in okuma tarafı yok.** `GET /products/{sku}` yok; ürün yöneticisi ne girdiğini göremiyor.
4. **Birden fazla instance'ta temizlik.** Her instance kendi temizlik zamanlayıcısını çalıştırıyor.
   Zararsız (DELETE idempotent) ama gereksiz. Lider seçimi gerekir mi?
5. **Hata hiyerarşisi iki context'te farklı.** Storefront'ta `RuleViolationException` yok;
   `ConcurrentUpdateException` bir `RuleViolationException` değil ama 409 dönüyor. Bilinçli mi, kaza mı?
6. **Test-jar bağımlılığı.** Application testleri domain-service'in test sınıflarına bağlı; sahte
   repository değişirse iki modülün testleri etkilenir.
7. **Faz 2'den kalanlar:** park kuyruğunu yeniden oynatma (6), sözleşme sürümü (7), polling gecikmesi (8).
   Bunlar mimari değil işletim konuları; bir sonraki fazın adayları.

## Senin listen
- [ ] ...
