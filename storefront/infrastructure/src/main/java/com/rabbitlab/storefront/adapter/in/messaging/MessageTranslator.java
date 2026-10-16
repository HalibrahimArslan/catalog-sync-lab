package com.rabbitlab.storefront.adapter.in.messaging;

import com.rabbitlab.contract.BackofficeEvents;
import com.rabbitlab.contract.BackofficeMessage;
import com.rabbitlab.contract.Money;
import com.rabbitlab.contract.ProductCreatedMessage;
import com.rabbitlab.contract.ProductCreatedMessageV2;
import com.rabbitlab.contract.ProductDeactivatedMessage;
import com.rabbitlab.contract.ProductUpdatedMessage;
import com.rabbitlab.contract.ProductUpdatedMessageV2;
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
 *
 * <p>P4-M3: iki sözleşme sürümünü de okur. Sürüm {@code x-schema-version} başlığında; başlık yoksa v1
 * (P4-M3 öncesi yayınlanmış, hâlâ kuyrukta ya da park kuyruğunda bekleyen mesajlar). Bilinmeyen sürüm
 * bozuk mesajdır: tekrar denemek işe yaramaz, park edilir; Storefront o sürümü öğrenince yeniden oynatılır.
 */
@Component
class MessageTranslator {

    private final JsonMapper json;

    MessageTranslator(JsonMapper json) {
        this.json = json;
    }

    CatalogUpdate translate(Message message) {
        String type = message.getMessageProperties().getType();
        Object versionHeader = message.getMessageProperties().getHeader(BackofficeEvents.SCHEMA_VERSION_HEADER);
        try {
            int version = schemaVersionOf(versionHeader);
            BackofficeMessage contract = json.readValue(message.getBody(), BackofficeEvents.classOf(type, version));
            return toUpdate(contract);
        } catch (JacksonException | InvalidValueException | IllegalArgumentException e) {
            // Bozuk JSON / eksik alan (Jackson), negatif stok (domain),
            // bilinmeyen tip ya da sürüm (sözleşme: IllegalArgumentException)
            throw new InvalidMessageException(
                    "Mesaj okunamadı (" + type + ", sürüm " + versionHeader + "): " + e.getMessage(), e);
        }
    }

    /** Başlık yoksa v1. AMQP tamsayıyı Integer ya da Long getirebilir; elle konmuş metin de olabilir. */
    private static int schemaVersionOf(Object header) {
        return switch (header) {
            case null -> 1;
            case Number number -> number.intValue();
            default -> {
                try {
                    yield Integer.parseInt(header.toString());
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Sözleşme sürümü okunamadı: " + header, e);
                }
            }
        };
    }

    private static CatalogUpdate toUpdate(BackofficeMessage message) {
        return switch (message) {
            case ProductCreatedMessage m -> new CatalogUpdate.NewProduct(new Sku(m.sku()), m.name(), m.description(),
                    new Price(m.price(), m.currency()), m.version());
            case ProductUpdatedMessage m -> new CatalogUpdate.DetailsChanged(new Sku(m.sku()), m.name(),
                    m.description(), new Price(m.price(), m.currency()), m.version());
            case ProductCreatedMessageV2 m -> new CatalogUpdate.NewProduct(new Sku(m.sku()), m.name(),
                    m.description(), priceOf(m.price()), m.version());
            case ProductUpdatedMessageV2 m -> new CatalogUpdate.DetailsChanged(new Sku(m.sku()), m.name(),
                    m.description(), priceOf(m.price()), m.version());
            case ProductDeactivatedMessage m -> new CatalogUpdate.Withdrawn(new Sku(m.sku()), m.version());
            case StockUpdatedMessage m -> new CatalogUpdate.StockChanged(new Sku(m.sku()), m.quantity(), m.version());
        };
    }

    /** Fiyat nesnesi eksikse NullPointerException değil domain hatası: geçici sanılıp tekrar denenmesin. */
    private static Price priceOf(Money money) {
        if (money == null) {
            throw new InvalidValueException("price boş olamaz");
        }
        return new Price(money.amount(), money.currency());
    }
}
