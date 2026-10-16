package com.rabbitlab.backoffice.adapter.out.persistence;

import com.rabbitlab.backoffice.domain.Sku;
import com.rabbitlab.backoffice.domain.inventory.Inventory;
import com.rabbitlab.backoffice.domainservice.ConcurrentUpdateException;
import com.rabbitlab.backoffice.domainservice.InventoryRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
class JdbcInventoryRepository implements InventoryRepository {

    private final JdbcClient jdbc;

    JdbcInventoryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Inventory> findBySku(Sku sku) {
        return jdbc.sql("select sku, quantity, version from inventory where sku = ?")
                .param(sku.value())
                .query((rs, row) -> Inventory.restore(
                        new Sku(rs.getString("sku")), rs.getInt("quantity"), rs.getLong("version")))
                .optional();
    }

    @Override
    public void save(Inventory inventory) {
        if (inventory.isNew()) {
            jdbc.sql("insert into inventory (sku, quantity, version) values (?, ?, ?)")
                    .params(inventory.sku().value(), inventory.quantity(), inventory.version())
                    .update();
            return;
        }
        int updated = jdbc.sql("update inventory set quantity = ?, version = ? where sku = ? and version = ?")
                .params(inventory.quantity(), inventory.version(), inventory.sku().value(),
                        inventory.persistedVersion())
                .update();
        if (updated == 0) {
            throw new ConcurrentUpdateException("Stok", inventory.sku().value(), inventory.persistedVersion());
        }
    }
}
