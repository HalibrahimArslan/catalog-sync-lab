package com.rabbitlab.storefront.application;

/**
 * Park edilmiş bir mesajın, sorunu çözecek insana gösterilen hâli.
 *
 * @param messageId yeniden oynatmak için kimlik; kimliksiz (bozuk) mesajda {@code null}
 * @param type      sözleşmedeki mesaj tipi ({@code StockUpdated} gibi)
 * @param error     park edilme sebebi
 * @param attempts  park edilene kadar kaç kez denendiği
 * @param body      mesajın içeriği, olduğu gibi
 */
public record ParkedMessage(String messageId, String type, String error, long attempts, String body) {
}
