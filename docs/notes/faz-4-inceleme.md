# Faz 4 — İnceleme Rehberi

Kodu Claude yazdı, sen inceliyorsun. Aynı düzen: `test:` (kırmızı) → `feat:` (yeşil). M4'te kırmızı aşaması
yok: kabul senaryoları M2 ve M3'te yazılmış davranışı uçtan uca doğruluyor. Güvence, senaryoların bilinçli
bir bozmayla kırmızıya dönmesi. **Önce testi oku, sonra kodu.**

```bash
git log --oneline p3-m5..phase-4-operations
git diff p4-m1 p4-m2 --stat         # park kuyruğu yönetimi: hangi halkaya ne eklendi
git diff p4-m2 p4-m3 -- contract    # kırıcı değişiklik sözleşmede nasıl görünüyor
./mvnw verify
```

Faz 1–3 mimariyle uğraştı; Faz 4 sistemi **çalıştıran** kişinin sorunlarını çözüyor. Mimari değişmedi
(Onion, halka başına modül): yeni her şey var olan halkalara oturdu.

## Faz 3'ten kalan acıtan noktalar → Faz 4'te ne oldu
| # | Faz 3 sonunda | Faz 4'te | Nerede |
|---|---|---|---|
| 8 | Relay 500 ms'de bir yokluyor; gecikme ve boş sorgu | Outbox'a satır eklenince PostgreSQL `NOTIFY` gönderir, relay anında uyanır; polling 5 sn'lik yedek | `V3__outbox_notify.sql`, `OutboxNotificationListener`, `PeriodicJob.runNow` |
| 6 | Park kuyruğu çıkmaz sokak; mesajı görmek de geri göndermek de elle RabbitMQ işi | Yönetim uç noktaları: listele (mesaj yerinde kalır), tek tek ya da toplu yeniden oynat; deneme sayacı sıfırlanır | `ParkedMessages`, `RabbitParkedMessages`, `ParkingController` |
| 7 | Sözleşmenin sürümü yok; kırıcı değişiklik iki uygulamanın aynı anda deploy edilmesini ister | Sürüm `x-schema-version` başlığında; Storefront v1 ve v2'yi okur; Backoffice'te sürüm ayarla seçilir; outbox satırı kendi sürümünü taşır | `BackofficeEvents`, `MessageTranslator`, `JdbcEventOutbox`, `V4__outbox_schema_version.sql` |

## Yönetim uç noktaları (Storefront, port 8082)
```bash
curl localhost:8082/admin/parking                                       # ["storefront.catalog","storefront.stock"]
curl 'localhost:8082/admin/parking/storefront.stock?limit=20'           # park edilenler: messageId, type, error, attempts, body
curl -X POST localhost:8082/admin/parking/storefront.stock/<messageId>/replay   # 204; yoksa 404
curl -X POST localhost:8082/admin/parking/storefront.stock/replay       # {"replayed": n}
```
Kimlik doğrulama yok (bilinçli kapsam dışı). Gerçek sistemde bu uç noktaları herkes çağırabilseydi mesaj
içeriklerini okuyabilir ve işlemeyi tetikleyebilirdi.

## Sözleşme sürümü: kırıcı değişikliğin güvenli sırası
```
  1. Tüketici iki sürümü okur        Storefront v1 + v2      Backoffice v1 yayınlar   ← P4-M3 burada bitti
  2. Üretici yeni sürüme geçer       Storefront v1 + v2      Backoffice v2 yayınlar   ← US7 bu adımı oynuyor
  3. Eski sürüm emekliye ayrılır     Storefront v2           Backoffice v2            ← yapılmadı (bkz. acıtan noktalar)
```
Sıra bozulursa (Backoffice, Storefront'tan önce yeni sürüme geçerse) mesaj kaybolmaz: Storefront bilmediği
sürümü bozuk sayıp park eder. Storefront güncellenince park kuyruğu toplu yeniden oynatılır. M2 ile M3
böyle birbirini tamamlıyor.

---

## M1 — Outbox trigger + LISTEN/NOTIFY (`p4-m1`)
**Oku:** `OutboxNotifyIT` → `V3__outbox_notify.sql`, `OutboxNotificationListener`, `PeriodicJob.runNow`, `OutboxRelayScheduler`
- Test, polling aralığını bir dakikaya çekip mesajın saniyeler içinde gitmesini bekliyor. Bu kurgu neyi kanıtlıyor?
- `NOTIFY` transaction commit olunca gidiyor. Commit'ten önce gitseydi relay ne görürdü?
- Trigger neden `FOR EACH STATEMENT`? `FOR EACH ROW` olsaydı 100 satırlık bir insert ne yapardı?
- Listener bağlanır bağlanmaz neden bir kez `wakeUp()` çağırıyor? Çağırmasaydı hangi satır kaçardı?
- `runNow()` art arda gelen istekleri tek tura birleştiriyor. Tur sürerken gelen bildirim kaybolur mu?
- Bildirim bağlantısı kopunca ne olur? Kopukluk ne kadar sürerse sürsün en kötü gecikme ne kadardır?

## M2 — Park kuyruğu yönetimi (`p4-m2`)
**Oku:** `ParkedMessagesIT`, `ParkingControllerTest` → `ParkedMessages`, `RabbitParkedMessages`, `ParkingController`
- `ParkedMessages` arayüzü neden application halkasında? Web ve messaging adapter'ları birbirini görebilseydi ne kolaylaşır, ne bozulurdu? (`CatalogQueries` ile karşılaştır.)
- RabbitMQ'da "kuyruğa bak ama alma" yok. `list` bunu nasıl taklit ediyor? Mesajların sırası neden bozulmuyor?
- `basicNack(0, true, true)`: tag 0 ve `multiple` ne anlama geliyor? Neden her işlemin `finally`'sinde?
- Yeniden oynatma sırası: yayınla → onay bekle → park kopyasını ack'le. Ters sırada yapılsaydı çökmede ne kaybolurdu? Bu sırada çökersek mesaj iki kez gider; kim kurtarır?
- `should_reset_attempt_counter_when_replaying`: `x-death` silinmeseydi yeniden oynatılan mesaj kaçıncı denemede park olurdu? (Test bunu bir bozmayla da gösterdi.)
- Mesaj neden topic exchange'e değil, doğrudan kuyruğa gönderiliyor?
- `replayAll` neden başladığı andaki mesaj sayısıyla sınırlı? "Kuyruk boşalana kadar" çalışsaydı bozuk bir mesajda ne olurdu?

## M3 — Sözleşme sürümü (`p4-m3`)
**Oku:** `SchemaVersionTest`, `MessageTranslatorTest`, `OutboxSchemaVersionIT`, `OutboxPropertiesTest` → `BackofficeEvents`, `Money`, `*MessageV2`, `ContractMapper`, `JdbcEventOutbox`, `OutboxRelay`, `MessageTranslator`
- Sürüm neden tip adında (`ProductCreated.v2`) değil de başlıkta? Tip adına gömülseydi routing key'ler ve `BackofficeEvents.TYPES` nasıl değişirdi?
- Stok ve pasifleştirme mesajları iki sürümde aynı record. O hâlde Backoffice bu mesajlara neden yine de "2" yazıyor?
- Relay sürümü ayardan değil satırdan okuyor. `should_publish_old_rows_with_their_own_version_after_setting_changed` olmasa hangi hata fark edilmezdi?
- Başlıksız mesajı v1 saymak neden gerekli? Hangi mesajlar başlıksız?
- Bilinmeyen sürüm neden geçici hata değil de bozuk mesaj? Geçici hata sayılsaydı 3 denemede ne değişirdi?
- v2'de fiyat nesnesi eksikse neden `NullPointerException` değil `InvalidValueException`? (İpucu: listener hangi exception'ı geçici sayıyor?)
- `backoffice.outbox.schema-version=3` ile uygulama açılmıyor. Açılsaydı hata ne zaman ve kimin önünde çıkardı?

## M4 — Kabul senaryoları (`p4-m4`)
**Oku:** `isletim.feature` → `ProductSyncSteps` (işletim bölümü), `SystemUnderTest.publishSchemaVersion`
- US6 park edilen mesajı RabbitMQ'ya dokunmadan, sadece HTTP ile görüyor ve geri gönderiyor. Neden önemli?
- "Sürüm geçişi sırasında bekleyen eski mesajlar" senaryosunda vitrin kapalıyken hem v1 hem v2 mesajlar birikiyor. Beklenen sürüm listesi neden `1, 1, 2, 2`?
- `@After` Backoffice'i v1'e döndürüyor. Döndürmeseydi senaryoların sırası neyi etkilerdi?

---

## Bilinçli bırakılan "acıtan noktalar"
İncelerken fark et, listeye kendi bulduklarını ekle:

1. **Yönetim uç noktalarında kimlik doğrulama yok.** Herkes park edilen mesajların içeriğini okuyabilir,
   yeniden oynatabilir. En azından ayrı bir port (`management.server.port` gibi) ve yetki gerekir.
2. **v1 emekliye ayrılmadı.** Güvenli sıranın üçüncü adımı yapılmadı: Storefront hâlâ v1 okuyor. v1 kodunu
   silmek için kuyruklarda, park kuyruğunda ve outbox'ta v1 mesaj kalmadığından emin olmak gerekir; bunu
   ölçen bir şey yok. Outbox sütununun varsayılanı da hâlâ 1.
3. **Sürüm üreticinin tek ayarı.** Birden fazla tüketici olsaydı Backoffice en yavaş tüketiciyi beklemek
   zorunda kalırdı. Kim hangi sürümü okuyor, sistem bunu bilmiyor (schema registry'nin çözdüğü sorun).
4. **Başlığı okumayan tüketici v2'yi yanlış okur.** Sürüm gövdede değil başlıkta; mesajı başlığa bakmadan
   işleyen bir tüketici (ör. bir log aracı, başka bir ekip) kırıcı değişikliği fark etmez.
5. **`replayAll` her şeyi tek kanalda ack'siz tutuyor.** Park kuyruğunda yüz bin mesaj varsa hepsi aynı
   anda ack bekler; parça parça (sayfa sayfa) yeniden oynatmak gerekir.
6. **Aynı anda iki operatör.** Biri listelerken diğeri yeniden oynatırsa, listeleyenin ack'siz tuttuğu
   mesajlar diğerine görünmez; "bulunamadı" (404) yanıtı yanıltıcı olabilir.
7. **LISTEN için ayrı bağlantı.** Her Backoffice instance'ı havuzdan bir bağlantıyı sürekli meşgul ediyor.
   PgBouncer'ın transaction modu `LISTEN`'ı desteklemez; araya bağlantı havuzlayıcı girerse M1 sessizce
   polling'e düşer.
8. **Park edilen mesajın geçmişi kayboluyor.** Yeniden oynatırken `x-death` ve `x-error` siliniyor; mesaj
   tekrar park edilirse önceki sebepler görünmüyor. Sayacı sıfırlamak ile geçmişi korumak ayrı başlıklar ister.

## Senin listen
- [ ] ...
