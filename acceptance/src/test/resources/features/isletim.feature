# language: tr
Özellik: Sistemi işleten kişi sorunları kodu değiştirmeden çözebilir
  Park edilen mesaj bir çıkmaz sokak değildir: sorunu düzelten kişi mesajı yeniden oynatır.
  Mesaj sözleşmesi kırıcı biçimde değişebilir: önce vitrin iki sürümü de okur, sonra Backoffice
  yeni sürüme geçer; geçiş sırasında bekleyen eski mesajlar da işlenir.

  Senaryo: Park edilen mesaj, sorun düzeltilince yeniden oynatılır (US6)
    Eğer ki vitrine hiç ulaşmamış bir ürün için stok mesajı gelir
    Ve mesaj 3 denemeden sonra park kuyruğunda görünür
    Ve ürün yöneticisi "Kupa" adlı ürünü 100 TRY fiyatla oluşturur
    Ve operatör park edilen mesajı yeniden oynatır
    O zaman vitrinde stok 4 olur
    Ve mesaj artık park kuyruğunda değildir

  Senaryo: Backoffice yeni sözleşme sürümüne geçer, vitrin kesintisiz güncellenir (US7)
    Diyelim ki vitrinde "Kupa" adlı ürün 100 TRY fiyatla var
    Eğer ki Backoffice sözleşmenin 2. sürümüyle yayınlamaya başlar
    Ve ürün yöneticisi fiyatı 120 TRY yapar
    O zaman vitrinde fiyat 120 TRY olur
    Ve Backoffice'in bu ürün için yayınladığı mesajların sürümleri "1, 2" olur

  Senaryo: Sürüm geçişi sırasında bekleyen eski mesajlar da işlenir (US7)
    Diyelim ki vitrinde "Kupa" adlı ürün 100 TRY fiyatla var
    Ve vitrin uygulaması kapalı
    Eğer ki ürün yöneticisi fiyatı 120 TRY yapar
    Ve Backoffice sözleşmenin 2. sürümüyle yayınlamaya başlar
    Ve ürün yöneticisi açıklamayı "Büyük boy" yapar
    Ve depo görevlisi stoğu 6 olarak sayar
    Ve vitrin uygulaması tekrar açılır
    O zaman vitrinde fiyat 120 TRY olur
    Ve vitrinde açıklama "Büyük boy" olur
    Ve vitrinde stok 6 olur
    Ve Backoffice'in bu ürün için yayınladığı mesajların sürümleri "1, 1, 2, 2" olur

  Senaryo: Vitrinin bilmediği sürümdeki mesaj tekrar denenmeden park edilir (US7)
    Eğer ki vitrinin henüz bilmediği 3. sürümde bir mesaj gelir
    O zaman bozuk mesaj tekrar denenmeden park kuyruğuna düşer
