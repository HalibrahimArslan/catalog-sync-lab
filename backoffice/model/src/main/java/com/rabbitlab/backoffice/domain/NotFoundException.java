package com.rabbitlab.backoffice.domain;

/** Aranan şey yok. Somut alt sınıflar neyin bulunamadığını söyler. */
public abstract class NotFoundException extends DomainException {

    protected NotFoundException(String message) {
        super(message);
    }
}
