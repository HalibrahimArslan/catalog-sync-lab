# Sözlük (Ubiquitous Language)

| Türkçe | Kodda | Context | Açıklama |
|---|---|---|---|
| Ürün | `Product` | Backoffice | Ürün bilgisinin (ad, açıklama, fiyat, aktiflik) asıl kaydı; aggregate |
| Stok | `Inventory` | Backoffice | Bir SKU'nun depodaki miktarı; ayrı aggregate, kendi versiyonu |
| Stok sayımı | `Inventory.count()` | Backoffice | Depo görevlisinin girdiği mutlak miktar |
| Katalog ürünü | `CatalogItem` | Storefront | Vitrindeki kopya |
| Katalog | `Catalog` | Storefront | Vitrindeki ürünlerin tamamı |
| Stok kodu | `sku` | İkisi | Ürünün benzersiz kimliği |
| Stok miktarı | `quantity` / `stock` | İkisi | Negatif olamaz |
| Fiyat | `price` + `currency` (v1), `price: Money` (v2) | İkisi | > 0 |
| Ürün versiyonu | `version` / `detailsVersion` | İkisi | Ürün bilgisi event'lerinde artan sayı |
| Stok versiyonu | `version` / `stockVersion` | İkisi | `StockUpdated`'de artan sayı |
| Aktif / Pasif | `active` | İkisi | Pasif ürün satışta görünmez; silme yok |
| Tükendi | `outOfStock()` | Storefront | Stok 0 |
| Ürün oluşturuldu | `ProductCreated` | Event | |
| Ürün güncellendi | `ProductUpdated` | Event | Ad, açıklama, fiyat |
| Stok güncellendi | `StockUpdated` | Event | Mutlak değer + stok versiyonu |
| Ürün pasife alındı | `ProductDeactivated` | Event | |
| Vitrin | Storefront | Context | E-ticaret tarafı |
| Park kuyruğu | `*.dlq` | Altyapı | İşlenemeyen mesajların bekletildiği yer (Dead Letter Queue) |
| Yeniden oynatma | `replay` | Storefront | Park edilen mesajı ana kuyruğa geri göndermek; deneme sayacı sıfırlanır |
| Sözleşme sürümü | `x-schema-version` | Sözleşme | Mesaj gövdesinin biçimi; başlıkta taşınır, başlıksız mesaj v1 |
