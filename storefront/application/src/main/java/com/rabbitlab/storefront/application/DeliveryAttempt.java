package com.rabbitlab.storefront.application;

/**
 * Teslimat geçmişine yazılan bir deneme.
 *
 * @param messageId     kimliksiz (bozuk) mesajda {@code null}
 * @param sku           gövdeden okunabildiyse
 * @param schemaVersion mesajın okunduğu sözleşme sürümü; başlık okunamadıysa {@code null}
 * @param attempt       kaçıncı deneme; yeniden oynatmada 0
 * @param error         sebep; başarılı denemede {@code null}
 */
public record DeliveryAttempt(String messageId, String queue, String type, String sku, Integer schemaVersion,
                              int attempt, DeliveryOutcome outcome, String error) {
}
