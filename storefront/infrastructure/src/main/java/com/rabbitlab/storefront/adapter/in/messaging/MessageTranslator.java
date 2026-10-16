package com.rabbitlab.storefront.adapter.in.messaging;

import com.rabbitlab.contract.BackofficeEvents;
import com.rabbitlab.contract.BackofficeMessage;
import com.rabbitlab.contract.ProductCreatedMessage;
import com.rabbitlab.contract.ProductDeactivatedMessage;
import com.rabbitlab.contract.ProductUpdatedMessage;
import com.rabbitlab.contract.StockUpdatedMessage;
import com.rabbitlab.storefront.application.CatalogUpdate;
import com.rabbitlab.storefront.domain.InvalidValueException;
import com.rabbitlab.storefront.domain.Price;
import com.rabbitlab.storefront.domain.Sku;
import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * AMQP mesajı → sözleşme record'u → Storefront'un dili ({@link CatalogUpdate}).
 *
 * <p>"Anti-corruption layer": dışarıdan gelen modelin Storefront'un içine sızmasını engeller.
 * Okunamayan veya kurallara aykırı her şey burada {@link InvalidMessageException} olur.
 */
@Component
class MessageTranslator {

    private final JsonMapper json;

    MessageTranslator(JsonMapper json) {
        this.json = json;
    }

    CatalogUpdate translate(Message message) {
        String type = message.getMessageProperties().getType();
        try {
            BackofficeMessage contract = json.readValue(message.getBody(), BackofficeEvents.classOf(type));
            return toUpdate(contract);
        } catch (JacksonException | InvalidValueException | IllegalArgumentException e) {
            // Bozuk JSON / eksik alan (Jackson), negatif stok (domain), bilinmeyen tip (sözleşme: IllegalArgumentException)
            throw new InvalidMessageException("Mesaj okunamadı (" + type + "): " + e.getMessage(), e);
        }
    }

    private static CatalogUpdate toUpdate(BackofficeMessage message) {
        return switch (message) {
            case ProductCreatedMessage m -> new CatalogUpdate.NewProduct(new Sku(m.sku()), m.name(), m.description(),
                    new Price(m.price(), m.currency()), m.version());
            case ProductUpdatedMessage m -> new CatalogUpdate.DetailsChanged(new Sku(m.sku()), m.name(),
                    m.description(), new Price(m.price(), m.currency()), m.version());
            case ProductDeactivatedMessage m -> new CatalogUpdate.Withdrawn(new Sku(m.sku()), m.version());
            case StockUpdatedMessage m -> new CatalogUpdate.StockChanged(new Sku(m.sku()), m.quantity(), m.version());
        };
    }
}
