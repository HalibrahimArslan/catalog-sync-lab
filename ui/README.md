# Senaryo Konsolu

Kabul senaryolarını (US1–US7) **çalışan sisteme karşı** adım adım koşturup izlemek için bir arayüz.
Cucumber testleri aynı senaryoları kendi açtığı konteynerlerde koşar; bu konsol ise senin elle
başlattığın Backoffice, Storefront ve RabbitMQ'ya bakar. Kapatıp açma gibi işleri sen yaparsın,
konsol talimat gösterip bekler.

## Çalıştırmak
```bash
# kökte
docker compose up -d
./mvnw -q package -DskipTests
java -jar backoffice/infrastructure/target/backoffice-0.4.0-SNAPSHOT-exec.jar &
java -jar storefront/infrastructure/target/storefront-0.4.0-SNAPSHOT-exec.jar &

# ui/
npm install
npm run dev        # http://localhost:5173
```
Üst sağdaki üç nokta servislerin ayakta olup olmadığını gösterir; yanındaki düğmeyle tema seçilir
(açık / koyu / sistem; seçim tarayıcıda hatırlanır).

## Sekmeler
| Sekme | Ne yapar |
|---|---|
| **Senaryolar** | 13 senaryo, hikâyelere göre gruplu. Her adım ✓ geçti / ✗ kaldı (sebebiyle) / ✋ seni bekliyor. "Elle adımı olmayanları çalıştır" kesinti gerektirmeyen 10 senaryoyu sırayla koşar |
| **Mesajlar** | Mesaj takibi (Celery'deki Flower gibi): her mesajın durumu (yayın bekliyor / yayınlanamıyor / yolda / işlendi / tekrar denenecek / park edildi / yeniden oynatıldı), deneme sayısı ve hatası. SKU ya da message-id ile ara, duruma göre süz; bir mesaja tıklayınca Backoffice'ten vitrine yolculuğu (her deneme, saati ve hatasıyla), gövdesi ve park edildiyse "Yeniden oynat" |
| **Elle dene** | Backoffice'e komut gönder (oluştur, güncelle, stok, satıştan kaldır); vitrinin ne zaman güncellendiğini yan tarafta izle |
| **Kuyruklar ve park** | Kuyruk derinlikleri; park kuyruklarındaki mesajlar, sebepleri ve deneme sayıları; tek tek ya da toplu yeniden oynatma |

Elle adım gerektiren senaryolar: **US5-1** (vitrini kapat/aç), **US7** geçiş senaryoları (Backoffice'i
`--backoffice.outbox.schema-version=2` ile yeniden başlat, sonra v1'e döndür). Her elle adımdan sonra
konsol, adımın gerçekten yapıldığını kontrol eder (ör. vitrin gerçekten cevap vermiyor mu).

US7'de mesajların hangi sürümle gittiğini görmek için konsol RabbitMQ'da geçici bir kuyruk
(`ui.tap.*`) açar ve exchange'e bağlar; senaryo bitince siler (unutulursa RabbitMQ 10 dakikada kendisi siler).

## Mesaj takibi nereden besleniyor?
Mesajın kimliği Backoffice outbox'ında üretilen `message_id`; RabbitMQ'da ve Storefront'ta da aynı.

| Kaynak | Uç nokta | Ne söyler |
|---|---|---|
| Backoffice outbox | `GET /admin/outbox?sku=&messageId=&state=PENDING\|FAILING\|PUBLISHED`, `/admin/outbox/stats` | Yazıldı mı, yayınlandı mı, yayınlanamıyorsa neden (`last_error`) |
| Storefront teslimat geçmişi (`message_attempt`) | `GET /admin/messages?sku=&messageId=&state=`, `/admin/messages/{id}`, `/admin/messages/stats` | Her deneme: işlendi / tekrar geldi / tekrar denenecek / park edildi / yeniden oynatıldı, hatasıyla |

RabbitMQ işlenen mesajı unutur ve tekrar denenen mesajın neden başarısız olduğunu hiçbir yere yazmaz;
teslimat geçmişi bu yüzden var (Celery'nin result backend'i gibi). Inbox ile aynı saklama süresinden
(`storefront.inbox.retention`, varsayılan 7 gün) sonra silinir.

## Nasıl bağlanıyor?
Tarayıcı üç servise Vite'ın proxy'si üzerinden ulaşır (`vite.config.ts`); aynı origin olduğu için
CORS ayarı gerekmez, RabbitMQ kimlik bilgisi tarayıcıya gitmez.

| Yol | Hedef | Değiştirmek için |
|---|---|---|
| `/svc/backoffice` | `http://localhost:8081` | `BACKOFFICE_URL` |
| `/svc/storefront` | `http://localhost:8082` | `STOREFRONT_URL` |
| `/svc/rabbitmq` | `http://localhost:15672/api` | `RABBITMQ_URL`, `RABBITMQ_USER`, `RABBITMQ_PASSWORD` |

Backoffice ve Storefront'un yönetim uç noktaları (`/admin/**`) HTTP Basic ister. Proxy, uygulamalarla
aynı `ADMIN_USER` / `ADMIN_PASSWORD` ortam değişkenlerini kullanır (yerelde varsayılan `admin` / `admin`);
uygulamaları başka bir şifreyle başlattıysan `npm run dev`'i de aynı değişkenlerle başlat.

## Kod
```
src/scenarios/engine.ts      senaryo motoru: adımlar sırayla, eventually(), elle adım onayı, iptal
src/scenarios/scenarios.ts   US1–US7 (adım adları .feature dosyalarıyla aynı)
src/scenarios/driver.ts      senaryoların sistemle tek temas noktası
src/api/http-driver.ts       gerçek sürücü: Backoffice, Storefront, RabbitMQ yönetim API'si
src/scenarios/fake-system.ts testler için bellekteki sistem
```
`npm test`: motor ve senaryo tanımları, bellekteki sahte sisteme karşı. Sahte vitrin sürüm başlığını
görmezden gelirse üç US7 senaryosunun da kaldığı ayrıca doğrulanıyor.
