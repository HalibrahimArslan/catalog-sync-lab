package com.rabbitlab.contract;

/** {@link ProductCreatedMessage}'ın v2 hâli: fiyat {@link Money} nesnesi. Tip adı ve routing key aynı. */
public record ProductCreatedMessageV2(String sku, String name, String description, Money price, long version)
        implements BackofficeMessage {
}
