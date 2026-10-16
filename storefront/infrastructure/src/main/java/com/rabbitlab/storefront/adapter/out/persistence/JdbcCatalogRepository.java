package com.rabbitlab.storefront.adapter.out.persistence;

import com.rabbitlab.storefront.domain.CatalogItem;
import com.rabbitlab.storefront.domain.Price;
import com.rabbitlab.storefront.domain.Sku;
import com.rabbitlab.storefront.domainservice.CatalogRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
class JdbcCatalogRepository implements CatalogRepository {

    private static final String COLUMNS = """
            sku, name, description, price_amount, price_currency, stock, active, details_version, stock_version""";

    private static final RowMapper<CatalogItem> MAPPER = (rs, row) -> CatalogItem.restore(
            new Sku(rs.getString("sku")),
            rs.getString("name"),
            rs.getString("description"),
            new Price(rs.getBigDecimal("price_amount"), rs.getString("price_currency")),
            rs.getInt("stock"),
            rs.getBoolean("active"),
            rs.getLong("details_version"),
            rs.getLong("stock_version"));

    private final JdbcClient jdbc;

    JdbcCatalogRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<CatalogItem> find(Sku sku) {
        return jdbc.sql("select " + COLUMNS + " from catalog_item where sku = ?")
                .param(sku.value()).query(MAPPER).optional();
    }

    /** {@code FOR UPDATE}: satır kilidi transaction sonuna kadar tutulur. */
    @Override
    public Optional<CatalogItem> findForUpdate(Sku sku) {
        return jdbc.sql("select " + COLUMNS + " from catalog_item where sku = ? for update")
                .param(sku.value()).query(MAPPER).optional();
    }


    /**
     * {@code ON CONFLICT DO NOTHING}: aynı ürün iki consumer'a aynı anda gelirse ikincisi hata almaz,
     * sessizce hiçbir şey yapmaz. "Önce var mı diye bak, yoksa ekle" iki adım olduğu için yarışa açıktır.
     */
    @Override
    public boolean insertIfAbsent(CatalogItem item) {
        return jdbc.sql("insert into catalog_item (" + COLUMNS + ") values (?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "on conflict (sku) do nothing")
                .params(item.sku().value(), item.name(), item.description(), item.price().amount(),
                        item.price().currency(), item.stock(), item.active(), item.detailsVersion(),
                        item.stockVersion())
                .update() == 1;
    }

    @Override
    public void update(CatalogItem item) {
        jdbc.sql("""
                        update catalog_item
                        set name = ?, description = ?, price_amount = ?, price_currency = ?, stock = ?, active = ?,
                            details_version = ?, stock_version = ?
                        where sku = ?""")
                .params(item.name(), item.description(), item.price().amount(), item.price().currency(),
                        item.stock(), item.active(), item.detailsVersion(), item.stockVersion(), item.sku().value())
                .update();
    }
}
