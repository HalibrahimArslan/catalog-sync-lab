package com.rabbitlab.backoffice.adapter.out.outbox;

import com.rabbitlab.backoffice.domain.event.DomainEvent;
import com.rabbitlab.backoffice.domain.event.ProductCreated;
import com.rabbitlab.backoffice.domain.event.ProductDeactivated;
import com.rabbitlab.backoffice.domain.event.ProductUpdated;
import com.rabbitlab.backoffice.domain.event.StockUpdated;
import com.rabbitlab.contract.BackofficeMessage;
import com.rabbitlab.contract.Money;
import com.rabbitlab.contract.ProductCreatedMessage;
import com.rabbitlab.contract.ProductCreatedMessageV2;
import com.rabbitlab.contract.ProductDeactivatedMessage;
import com.rabbitlab.contract.ProductUpdatedMessage;
import com.rabbitlab.contract.ProductUpdatedMessageV2;
import com.rabbitlab.contract.StockUpdatedMessage;

/**
 * Domain event'i → mesaj sözleşmesi. Faz 1'de bu iki şey aynı record'du (acıtan nokta 4).
 *
 * <p>Domain'de {@code Price} bir value object; kabloda {@code price} + {@code currency} iki alan.
 * Domain'de bir alanın adı değişirse sadece bu sınıf değişir, Storefront etkilenmez.
 *
 * <p>P4-M3: v2'de fiyat kabloda da tek nesne ({@link Money}), domain'deki {@code Price}'a daha yakın.
 * Fiyat taşımayan event'ler iki sürümde aynı mesaja çevrilir.
 */
final class ContractMapper {

    private ContractMapper() {
    }

    static BackofficeMessage toMessage(DomainEvent event, int schemaVersion) {
        return switch (schemaVersion) {
            case 1 -> toV1(event);
            case 2 -> toV2(event);
            default -> throw new IllegalArgumentException("Desteklenmeyen sözleşme sürümü: " + schemaVersion);
        };
    }

    private static BackofficeMessage toV1(DomainEvent event) {
        return switch (event) {
            case ProductCreated e -> new ProductCreatedMessage(e.sku().value(), e.name(), e.description(),
                    e.price().amount(), e.price().currency(), e.version());
            case ProductUpdated e -> new ProductUpdatedMessage(e.sku().value(), e.name(), e.description(),
                    e.price().amount(), e.price().currency(), e.version());
            case ProductDeactivated e -> new ProductDeactivatedMessage(e.sku().value(), e.version());
            case StockUpdated e -> new StockUpdatedMessage(e.sku().value(), e.quantity(), e.version());
        };
    }

    private static BackofficeMessage toV2(DomainEvent event) {
        return switch (event) {
            case ProductCreated e -> new ProductCreatedMessageV2(e.sku().value(), e.name(), e.description(),
                    new Money(e.price().amount(), e.price().currency()), e.version());
            case ProductUpdated e -> new ProductUpdatedMessageV2(e.sku().value(), e.name(), e.description(),
                    new Money(e.price().amount(), e.price().currency()), e.version());
            case ProductDeactivated e -> new ProductDeactivatedMessage(e.sku().value(), e.version());
            case StockUpdated e -> new StockUpdatedMessage(e.sku().value(), e.quantity(), e.version());
        };
    }
}
