package com.rabbitlab.storefront.application;

import com.rabbitlab.storefront.domain.CatalogItem;
import com.rabbitlab.storefront.domain.InvalidValueException;
import com.rabbitlab.storefront.domain.Sku;
import com.rabbitlab.storefront.domainservice.CatalogRepository;
import com.rabbitlab.storefront.domainservice.UnknownProductException;


/**
 * Kataloğu güncelleyen application servisi (yazma tarafı). Kalıp: kilitleyerek yükle → domain'e sor →
 * değiştiyse kaydet. Okuma için {@link CatalogQueries} (P3-M3).
 *
 * <p>Neden kilit (pessimistic)? Backoffice'te optimistic locking kullandık: çakışma nadir, çakışınca
 * kullanıcıya "tekrar dene" denir. Burada ise katalog ve stok consumer'ları AYNI satıra sürekli yazıyor;
 * çakışma olağan. Hata verip mesajı tekrar kuyruğa atmak yerine kısa süre sırada beklemek daha ucuz.
 */
public final class CatalogService {

    private final CatalogRepository repository;
    private final ProcessedUpdates processed;
    private final Transaction transaction;

    public CatalogService(CatalogRepository repository, ProcessedUpdates processed, Transaction transaction) {
        this.repository = repository;
        this.processed = processed;
        this.transaction = transaction;
    }

    /**
     * Backoffice'ten gelen bir değişikliği kataloğa uygular. Aynı kimlikli değişiklik ikinci kez gelirse
     * hiçbir şey yapmaz. Kimlik kaydı ve güncelleme tek transaction: biri olmazsa hiçbiri olmaz.
     *
     * @param updateId değişikliğin kimliği (Backoffice outbox'ındaki message_id)
     * @throws UnknownProductException ürün katalogda yoksa (tekrar denenebilir)
     * @throws InvalidValueException kimlik yoksa veya değişiklik kurallara aykırıysa (tekrar denemek işe yaramaz)
     */
    public void apply(String updateId, CatalogUpdate update) {
        if (updateId == null || updateId.isBlank()) {
            throw new InvalidValueException("Değişiklik kimliği (message-id) boş olamaz: " + update);
        }
        transaction.execute(() -> {
            if (!processed.markProcessed(updateId)) {
                return; // daha önce işlendi
            }
            switch (update) {
                case CatalogUpdate.NewProduct p -> repository.insertIfAbsent(
                        CatalogItem.register(p.sku(), p.name(), p.description(), p.price(), p.version()));
                case CatalogUpdate.DetailsChanged d -> change(d.sku(),
                        item -> item.changeDetails(d.name(), d.description(), d.price(), d.version()));
                case CatalogUpdate.StockChanged s -> change(s.sku(), item -> item.changeStock(s.quantity(), s.version()));
                case CatalogUpdate.Withdrawn w -> change(w.sku(), item -> item.withdraw(w.version()));
            }
        });
    }

    private void change(Sku sku, Change change) {
        CatalogItem item = repository.getForUpdate(sku);
        if (change.applyTo(item)) {
            repository.update(item);
        }
    }



    @FunctionalInterface
    private interface Change {
        boolean applyTo(CatalogItem item);
    }
}
