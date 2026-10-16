package com.rabbitlab.storefront.domain;

/** Değer kurallara aykırı: böyle bir nesne hiç oluşturulamaz. */
public class InvalidValueException extends DomainException {

    public InvalidValueException(String message) {
        super(message);
    }
}
