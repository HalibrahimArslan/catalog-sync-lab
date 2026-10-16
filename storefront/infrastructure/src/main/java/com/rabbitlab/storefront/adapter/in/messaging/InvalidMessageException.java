package com.rabbitlab.storefront.adapter.in.messaging;

/** Mesaj okunamıyor ya da kurallara aykırı. Tekrar denemek işe yaramaz (poison message). */
class InvalidMessageException extends RuntimeException {

    InvalidMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}
