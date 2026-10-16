package com.rabbitlab.backoffice.domain;

/** Değer kurallara aykırı: böyle bir nesne hiç oluşturulamaz. */
public class InvalidValueException extends DomainException {

    public InvalidValueException(String message) {
        super(message);
    }
}
