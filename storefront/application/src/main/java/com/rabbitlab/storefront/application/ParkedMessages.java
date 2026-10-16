package com.rabbitlab.storefront.application;

import java.util.List;

/**
 * Park edilmiş mesajlar: artık otomatik denenmeyen, bir insanın bakmasını bekleyen güncellemeler
 * (P4-M2, acıtan nokta 6). Uygulaması en dış halkada, RabbitMQ'nun park kuyruklarıyla.
 *
 * <p>Neden bu halkada? {@link CatalogQueries} ile aynı sebep: web ve messaging iki ayrı adapter,
 * birbirini tanımaz. Yönetim uç noktası park kuyruğuna ancak içteki bir arayüz üzerinden ulaşır.
 * Orkestre edilecek akış olmadığı için araya servis konmadı.
 *
 * <p>{@code queue}: güncellemenin tüketildiği kuyruk ({@link #queues()} listesinden biri); park
 * kuyruğunun adını uygulama bilir.
 */
public interface ParkedMessages {

    /** Park kuyruğu olan tüketici kuyrukları. */
    List<String> queues();

    /** Park kuyruğunun başındaki en fazla {@code limit} mesaj; mesajlar yerinde kalır. */
    List<ParkedMessage> list(String queue, int limit);

    /**
     * Mesajı ana kuyruğa geri gönderir; deneme sayacı sıfırdan başlar.
     *
     * @return mesaj park kuyruğunda bulunamadıysa {@code false}
     */
    boolean replay(String queue, String messageId);

    /**
     * Park kuyruğundaki mesajları ana kuyruğa geri gönderir. Sadece başladığı anda kuyrukta olanlar:
     * bu sırada tekrar park edilen mesaj ikinci kez gönderilmez.
     *
     * @return geri gönderilen mesaj sayısı
     */
    int replayAll(String queue);
}
