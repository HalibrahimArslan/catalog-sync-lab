package com.rabbitlab.contract;

/**
 * Backoffice'in yayınladığı tüm mesajların ortak tipi. Sadece veri taşır; davranış yok.
 *
 * <p>Alanlar ilkel tipler (String, BigDecimal, long): sözleşme hiçbir context'in domain
 * nesnesini bilmez. Storefront bu record'ları kendi modeline çevirir.
 */
public sealed interface BackofficeMessage
        permits ProductCreatedMessage, ProductUpdatedMessage, ProductDeactivatedMessage, StockUpdatedMessage,
        ProductCreatedMessageV2, ProductUpdatedMessageV2 {

    String sku();

    long version();
}
