package com.scc.dao;

import com.scc.model.DeliveryJob;
import com.scc.model.DeliveryStatus;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class DeliveryJobDao extends BaseDao {

    private static final String COLUMNS =
            "id, linkedRequestId, pickupLocation, dropLocation, quantityKg, assignedDriver, status, signatureConfirmed";

    private static DeliveryJob map(ResultSet rs) throws SQLException {
        return new DeliveryJob(rs.getInt("id"), rs.getInt("linkedRequestId"), rs.getString("pickupLocation"),
                rs.getString("dropLocation"), rs.getInt("quantityKg"), rs.getString("assignedDriver"),
                DeliveryStatus.valueOf(rs.getString("status")), rs.getInt("signatureConfirmed") == 1);
    }

    public DeliveryJob insert(int linkedRequestId, String pickup, String drop, int quantityKg) {
        int id = insertRow("INSERT INTO delivery_jobs (linkedRequestId, pickupLocation, dropLocation, quantityKg, " +
                        "assignedDriver, status, signatureConfirmed) VALUES (?, ?, ?, ?, NULL, 'UNASSIGNED', 0)",
                linkedRequestId, pickup, drop, quantityKg);
        return findById(id);
    }

    public DeliveryJob findById(int id) {
        return queryOne("SELECT " + COLUMNS + " FROM delivery_jobs WHERE id = ?", DeliveryJobDao::map, id);
    }

    public List<DeliveryJob> findAll() {
        return query("SELECT " + COLUMNS + " FROM delivery_jobs ORDER BY id", DeliveryJobDao::map);
    }

    public List<DeliveryJob> findByStatus(DeliveryStatus status) {
        return query("SELECT " + COLUMNS + " FROM delivery_jobs WHERE status = ? ORDER BY id",
                DeliveryJobDao::map, status.name());
    }

    public List<DeliveryJob> findByDriver(String driverUsername) {
        return query("SELECT " + COLUMNS + " FROM delivery_jobs WHERE assignedDriver = ? ORDER BY id",
                DeliveryJobDao::map, driverUsername);
    }

    /** DELIVERED jobs for a branch whose linked request is not COMPLETED yet (i.e. still waiting for the branch). */
    public List<DeliveryJob> findAwaitingConfirmation(String branchName) {
        return query("SELECT j.id, j.linkedRequestId, j.pickupLocation, j.dropLocation, j.quantityKg, " +
                "j.assignedDriver, j.status, j.signatureConfirmed " +
                "FROM delivery_jobs j JOIN stock_requests r ON r.id = j.linkedRequestId " +
                "WHERE j.dropLocation = ? AND j.status = 'DELIVERED' AND r.status <> 'COMPLETED' " +
                "ORDER BY j.id", DeliveryJobDao::map, branchName);
    }

    public void assignDriver(int jobId, String driverUsername) {
        updateRows("UPDATE delivery_jobs SET assignedDriver = ?, status = 'ASSIGNED' WHERE id = ?",
                driverUsername, jobId);
    }

    public void updateStatus(int jobId, DeliveryStatus status) {
        updateRows("UPDATE delivery_jobs SET status = ? WHERE id = ?", status.name(), jobId);
    }

    public void setSignatureConfirmed(int jobId, boolean confirmed) {
        updateRows("UPDATE delivery_jobs SET signatureConfirmed = ? WHERE id = ?", confirmed ? 1 : 0, jobId);
    }

    public int countActive() {
        Integer count = queryOne("SELECT COUNT(*) AS c FROM delivery_jobs " +
                "WHERE status IN ('ASSIGNED', 'ON_THE_WAY')", rs -> rs.getInt("c"));
        return count == null ? 0 : count;
    }
}