package com.rabbitlab.storefront.adapter.out.persistence;

import com.rabbitlab.storefront.application.CatalogQueries;
import com.rabbitlab.storefront.application.CatalogView;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Okuma tarafı: tablodan doğrudan görünüme. {@link JdbcCatalogRepository} ile aynı tabloyu okur ama
 * domain nesnesi kurmaz, kilit almaz, sadece vitrinin istediği sütunları seçer. İleride okuma
 * ayrı bir tabloya/replikaya taşınırsa sadece bu sınıf değişir.
 */
@Component
class JdbcCatalogQueries implements CatalogQueries {

    private static final String SELECT = """
            select sku, name, description, price_amount, price_currency, stock, stock = 0 as out_of_stock, active
            from catalog_item""";

    private static final RowMapper<CatalogView> VIEW = (rs, row) -> new CatalogView(
            rs.getString("sku"),
            rs.getString("name"),
            rs.getString("description"),
            rs.getBigDecimal("price_amount"),
            rs.getString("price_currency"),
            rs.getInt("stock"),
            rs.getBoolean("out_of_stock"),
            rs.getBoolean("active"));

    private final JdbcClient jdbc;

    JdbcCatalogQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<CatalogView> find(String sku) {
        return jdbc.sql(SELECT + " where sku = ?").param(sku).query(VIEW).optional();
    }

    @Override
    public List<CatalogView> visible() {
        return jdbc.sql(SELECT + " where active order by sku").query(VIEW).list();
    }
}
