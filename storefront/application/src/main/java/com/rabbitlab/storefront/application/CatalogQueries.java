package com.rabbitlab.storefront.application;

import java.util.List;
import java.util.Optional;

/**
 * Vitrin sorguları (okuma tarafı). Yazma tarafından ({@link CatalogService} → domain → repository)
 * tamamen ayrı: domain modelini yüklemez, kural çalıştırmaz, kilit almaz. Uygulaması en dış halkada,
 * doğrudan SQL ile.
 *
 * <p>Neden application servisi değil de arayüz? Okumada orkestre edilecek bir akış yok; araya bir
 * servis koymak Faz 2'deki port törenini geri getirirdi. Web bu arayüzü doğrudan kullanır.
 */
public interface CatalogQueries {

    Optional<CatalogView> find(String sku);

    /** Sitede görünen (aktif) ürünler, SKU sırasıyla. */
    List<CatalogView> visible();
}
