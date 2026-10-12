package com.scc.dao;

import com.scc.model.RequestStatus;
import com.scc.model.RequestType;
import com.scc.model.StockRequest;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class StockRequestDao extends BaseDao {

    private static final String COLUMNS = "id, requestingBranch, sourceBranch, type, quantityKg, status";

    private static StockRequest map(ResultSet rs) throws SQLException {
        return new StockRequest(rs.getInt("id"), rs.getString("requestingBranch"), rs.getString("sourceBranch"),
                RequestType.valueOf(rs.getString("type")), rs.getInt("quantityKg"),
                RequestStatus.valueOf(rs.getString("status")));
    }

    public StockRequest insert(String requestingBranch, String sourceBranch, RequestType type, int quantityKg) {
        int id = insertRow("INSERT INTO stock_requests (requestingBranch, sourceBranch, type, quantityKg, status) " +
                        "VALUES (?, ?, ?, ?, 'PENDING')",
                requestingBranch, sourceBranch, type.name(), quantityKg);
        return findById(id);
    }

    public StockRequest findById(int id) {
        return queryOne("SELECT " + COLUMNS + " FROM stock_requests WHERE id = ?", StockRequestDao::map, id);
    }

    public List<StockRequest> findAll() {
        return query("SELECT " + COLUMNS + " FROM stock_requests ORDER BY id", StockRequestDao::map);
    }

    public List<StockRequest> findByBranch(String branchName) {
        return query("SELECT " + COLUMNS + " FROM stock_requests WHERE requestingBranch = ? ORDER BY id",
                StockRequestDao::map, branchName);
    }

    public List<StockRequest> findByStatus(RequestStatus status) {
        return query("SELECT " + COLUMNS + " FROM stock_requests WHERE status = ? ORDER BY id",
                StockRequestDao::map, status.name());
    }

    public void updateStatus(int id, RequestStatus status) {
        updateRows("UPDATE stock_requests SET status = ? WHERE id = ?", status.name(), id);
    }
}