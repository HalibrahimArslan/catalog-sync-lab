package com.rabbitlab.storefront.application;

/**
 * Inbox: işlenmiş değişikliklerin kimlikleri. {@link CatalogService} ile aynı transaction'da çalışır;
 * güncelleme geri alınırsa kayıt da geri alınır ve değişiklik tekrar denenebilir.
 *
 * <p>Application halkasında, çünkü "aynı değişikliği iki kez uygulama" bir use case kuralı; mesajın
 * RabbitMQ'dan mı yoksa başka bir yerden mi geldiğinden bağımsız.
 */
public interface ProcessedUpdates {

    /** @return ilk kez görüldüyse {@code true}; daha önce işlendiyse {@code false} */
    boolean markProcessed(String updateId);
}
