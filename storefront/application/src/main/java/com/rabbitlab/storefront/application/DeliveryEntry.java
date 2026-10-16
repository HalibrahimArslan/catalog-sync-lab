package com.rabbitlab.storefront.application;

import java.time.Instant;

/** Bir mesajın geçmişindeki tek deneme. */
public record DeliveryEntry(int attempt, DeliveryOutcome outcome, String error, String queue, Instant at) {
}
