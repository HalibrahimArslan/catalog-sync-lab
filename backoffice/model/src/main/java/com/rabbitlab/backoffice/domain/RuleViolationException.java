package com.rabbitlab.backoffice.domain;

/** İstek geçerli ama nesnenin mevcut durumuyla çelişiyor (ör. pasif ürünü güncellemek, alınmış SKU). */
public abstract class RuleViolationException extends DomainException {

    protected RuleViolationException(String message) {
        super(message);
    }
}
