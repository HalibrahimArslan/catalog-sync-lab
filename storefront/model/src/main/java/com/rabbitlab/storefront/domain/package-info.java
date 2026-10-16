/**
 * Storefront'un modeli: vitrindeki ürün kopyası ve onu güncel tutma kuralları.
 *
 * <p>Backoffice'in {@code Product}'ı ile aynı isimleri kullansa da ayrı bir model. Storefront ürün
 * oluşturmaz, fiyat belirlemez; sadece gelen bilgiyi doğru sırayla yansıtır. Bu yüzden kuralları
 * farklı: "eski versiyonu yok say" Backoffice'te yoktur, burada çekirdek kuraldır.
 */
package com.rabbitlab.storefront.domain;
