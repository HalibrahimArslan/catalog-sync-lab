package com.rabbitlab.storefront.application;

/** Bir mesajı teslim alma denemesinin sonucu (mesaj takibi). Mesajın durumu, son denemesinin sonucudur. */
public enum DeliveryOutcome {
    /** Kataloğa uygulandı. */
    PROCESSED,
    /** Aynı kimlik daha önce işlenmişti; hiçbir şey yapılmadı (inbox). */
    DUPLICATE,
    /** Geçici hata; bekleme odasından sonra tekrar denenecek. */
    RETRY,
    /** Artık otomatik denenmeyecek; park kuyruğunda bir insanı bekliyor. */
    PARKED,
    /** Bir insan park kuyruğundan ana kuyruğa geri gönderdi. Deneme sayılmaz. */
    REPLAYED
}
