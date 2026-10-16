package com.rabbitlab.backoffice.adapter.out.persistence;

import com.rabbitlab.backoffice.domain.Sku;
import com.rabbitlab.backoffice.domain.product.Price;
import com.rabbitlab.backoffice.domain.product.Product;
import com.rabbitlab.backoffice.domainservice.ConcurrentUpdateException;
import com.rabbitlab.backoffice.domainservice.DuplicateSkuException;
import com.rabbitlab.backoffice.domainservice.ProductRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * {@link ProductRepository} port'unun PostgreSQL uygulaması. ORM yok; SQL açıkça görünsün diye
 * {@link JdbcClient}. Domain nesnesi ile tablo satırı arasındaki çeviri burada.
 */
@Repository
class JdbcProductRepository implements ProductRepository {

    private final JdbcClient jdbc;

    JdbcProductRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Product> findBySku(Sku sku) {
        return jdbc.sql("""
                        select sku, name, description, price_amount, price_currency, active, version
                        from product where sku = ?""")
                .param(sku.value())
                .query((rs, row) -> Product.restore(
                        new Sku(rs.getString("sku")),
                        rs.getString("name"),
                        rs.getString("description"),
                        new Price(rs.getBigDecimal("price_amount"), rs.getString("price_currency")),
                        rs.getBoolean("active"),
                        rs.getLong("version")))
                .optional();
    }

    @Override
    public void save(Product product) {
        if (product.isNew()) {
            insert(product);
        } else {
            update(product);
        }
    }

    /**
     * Servis, SKU'nun boş olduğunu önceden kontrol ediyor; ama iki istek aynı anda gelirse ikisi de
     * kontrolü geçebilir. Son sözü veritabanının PRIMARY KEY'i söyler.
     */
    private void insert(Product product) {
        try {
            jdbc.sql("""
                            insert into product (sku, name, description, price_amount, price_currency, active, version)
                            values (?, ?, ?, ?, ?, ?, ?)""")
                    .params(product.sku().value(), product.name(), product.description(), product.price().amount(),
                            product.price().currency(), product.active(), product.version())
                    .update();
        } catch (DuplicateKeyException e) {
            throw new DuplicateSkuException(product.sku());
        }
    }

    /** Optimistic locking: satır, okuduğumuz versiyondaysa güncellenir; değilse 0 satır etkilenir. */
    private void update(Product product) {
        int updated = jdbc.sql("""
                        update product
                        set name = ?, description = ?, price_amount = ?, price_currency = ?, active = ?, version = ?
                        where sku = ? and version = ?""")
                .params(product.name(), product.description(), product.price().amount(), product.price().currency(),
                        product.active(), product.version(), product.sku().value(), product.persistedVersion())
                .update();
        if (updated == 0) {
            throw new ConcurrentUpdateException("Ürün", product.sku().value(), product.persistedVersion());
        }
    }
}
