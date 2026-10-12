package com.scc.dao;

public class BranchStockDao extends BaseDao {

    /** Creates the branch row (0 kg) if it doesn't exist yet. */
    public void ensureBranch(String branchName) {
        updateRows("INSERT OR IGNORE INTO branch_stock (branchName, quantityKg) VALUES (?, 0)", branchName);
    }

    public boolean exists(String branchName) {
        return queryOne("SELECT 1 AS found FROM branch_stock WHERE branchName = ?",
                rs -> rs.getInt("found"), branchName) != null;
    }

    public int getStock(String branchName) {
        Integer qty = queryOne("SELECT quantityKg FROM branch_stock WHERE branchName = ?",
                rs -> rs.getInt("quantityKg"), branchName);
        return qty == null ? 0 : qty;
    }

    /** Adds (or subtracts, if negative) kg. Never goes below 0. */
    public void addStock(String branchName, int deltaKg) {
        updateRows("UPDATE branch_stock SET quantityKg = MAX(0, quantityKg + ?) WHERE branchName = ?",
                deltaKg, branchName);
    }
}