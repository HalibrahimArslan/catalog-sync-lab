package com.rabbitlab.storefront.adapter.in.web;

import com.rabbitlab.storefront.application.CatalogQueries;
import com.rabbitlab.storefront.application.CatalogView;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Vitrinin REST girişi (sadece okuma). Okuma tarafının görünümünü ({@link CatalogView}) döndürür;
 * domain modelini hiç görmez (P3-M3, mimari testte kural olarak da var).
 */
@RestController
@RequestMapping("/catalog")
class CatalogController {

    private final CatalogQueries queries;

    CatalogController(CatalogQueries queries) {
        this.queries = queries;
    }

    /** Sitede görünen (aktif) ürünler. */
    @GetMapping
    List<CatalogView> visibleItems() {
        return queries.visible();
    }

    /** Tek ürün; pasif olsa da döner (vitrinde gizli ama kaydı var). */
    @GetMapping("/{sku}")
    ResponseEntity<CatalogView> item(@PathVariable String sku) {
        return ResponseEntity.of(queries.find(sku));
    }
}
