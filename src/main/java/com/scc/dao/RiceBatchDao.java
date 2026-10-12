package com.scc.dao;

import com.scc.model.RiceBatch;
import com.scc.model.RiceStatus;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class RiceBatchDao extends BaseDao {

    private static final String COLUMNS = "id, supplierName, quantityKg, arrivalOrder, status";

    private static RiceBatch map(ResultSet rs) throws SQLException {
        return new RiceBatch(rs.getInt("id"), rs.getString("supplierName"), rs.getInt("quantityKg"),
                rs.getInt("arrivalOrder"), RiceStatus.valueOf(rs.getString("status")));
    }

    /** New arrivals get the next arrivalOrder number (strictly increasing -> FIFO). */
    public RiceBatch insert(String supplier, int quantityKg) {
        int id = insertRow("INSERT INTO rice_batches (supplierName, quantityKg, arrivalOrder, status) " +
                        "VALUES (?, ?, (SELECT COALESCE(MAX(arrivalOrder), 0) + 1 FROM rice_batches), 'IN_STOCK')",
                supplier, quantityKg);
        return findById(id);
    }

    /** Separate DAMAGED record split off from a batch (clean audit trail). Keeps the old arrivalOrder. */
    public void insertDamagedPortion(String supplier, int quantityKg, int arrivalOrder) {
        insertRow("INSERT INTO rice_batches (supplierName, quantityKg, arrivalOrder, status) " +
                "VALUES (?, ?, ?, 'DAMAGED')", supplier, quantityKg, arrivalOrder);
    }

    public RiceBatch findById(int id) {
        return queryOne("SELECT " + COLUMNS + " FROM rice_batches WHERE id = ?", RiceBatchDao::map, id);
    }

    public List<RiceBatch> findAll() {
        return query("SELECT " + COLUMNS + " FROM rice_batches ORDER BY id", RiceBatchDao::map);
    }

    /** In-stock batches, oldest first (FIFO). */
    public List<RiceBatch> findInStockFifo() {
        return query("SELECT " + COLUMNS + " FROM rice_batches WHERE status = 'IN_STOCK' " +
                "ORDER BY arrivalOrder ASC, id ASC", RiceBatchDao::map);
    }

    public int getTotalInStockKg() {
        Integer total = queryOne("SELECT COALESCE(SUM(quantityKg), 0) AS total FROM rice_batches " +
                "WHERE status = 'IN_STOCK'", rs -> rs.getInt("total"));
        return total == null ? 0 : total;
    }

    public void update(int id, int quantityKg, RiceStatus status) {
        updateRows("UPDATE rice_batches SET quantityKg = ?, status = ? WHERE id = ?",
                quantityKg, status.name(), id);
    }
}