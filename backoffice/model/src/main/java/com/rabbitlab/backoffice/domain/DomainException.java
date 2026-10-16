package com.rabbitlab.backoffice.domain;

/**
 * İş kuralından doğan tüm hataların tabanı. Dış halka (web, mesajlaşma) tek tek sınıfları değil,
 * bu hiyerarşinin türlerini tanır:
 * <ul>
 *   <li>{@link InvalidValueException}: değer kurallara aykırı (negatif stok, boş ad). Tekrar denemek işe yaramaz.</li>
 *   <li>{@link NotFoundException}: aranan şey yok.</li>
 *   <li>{@link RuleViolationException}: değer geçerli ama mevcut durumla çelişiyor (pasif ürünü güncellemek).</li>
 * </ul>
 * Faz 2'de hatalar üç ayrı halkaya dağılmıştı ve ortak bir tipleri yoktu (acıtan nokta 3).
 */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }
}
