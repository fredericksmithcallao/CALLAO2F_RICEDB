package com.scc.dao;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Makes sure every required table exists (and the pre-seeded SUPER_ADMIN account is present)
 * BEFORE the console menu starts. Safe to run on every launch: CREATE TABLE IF NOT EXISTS and
 * INSERT OR IGNORE never touch data that is already there.
 */
public class DatabaseInitializer {

    public void initialize() {
        createTables();
        seedSuperAdmin();
    }

    private void createTables() {
        try (Statement st = DatabaseConnection.getConnection().createStatement()) {

            // branch_stock first, because users / stock_requests point at it
            st.execute("""
                CREATE TABLE IF NOT EXISTS branch_stock (
                    branchName TEXT PRIMARY KEY COLLATE NOCASE,
                    quantityKg INTEGER NOT NULL DEFAULT 0
                )""");

            st.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    id         INTEGER PRIMARY KEY AUTOINCREMENT,
                    username   TEXT NOT NULL UNIQUE COLLATE NOCASE,
                    password   TEXT NOT NULL,
                    fullName   TEXT NOT NULL,
                    role       TEXT NOT NULL,
                    status     TEXT NOT NULL,
                    branchName TEXT COLLATE NOCASE,
                    FOREIGN KEY (branchName) REFERENCES branch_stock(branchName)
                )""");

            st.execute("""
                CREATE TABLE IF NOT EXISTS rice_batches (
                    id           INTEGER PRIMARY KEY AUTOINCREMENT,
                    supplierName TEXT NOT NULL,
                    quantityKg   INTEGER NOT NULL,
                    arrivalOrder INTEGER NOT NULL,
                    status       TEXT NOT NULL
                )""");

            st.execute("""
                CREATE TABLE IF NOT EXISTS stock_requests (
                    id               INTEGER PRIMARY KEY AUTOINCREMENT,
                    requestingBranch TEXT NOT NULL COLLATE NOCASE,
                    sourceBranch     TEXT COLLATE NOCASE,
                    type             TEXT NOT NULL,
                    quantityKg       INTEGER NOT NULL,
                    status           TEXT NOT NULL,
                    FOREIGN KEY (requestingBranch) REFERENCES branch_stock(branchName),
                    FOREIGN KEY (sourceBranch)     REFERENCES branch_stock(branchName)
                )""");

            st.execute("""
                CREATE TABLE IF NOT EXISTS delivery_jobs (
                    id                 INTEGER PRIMARY KEY AUTOINCREMENT,
                    linkedRequestId    INTEGER NOT NULL,
                    pickupLocation     TEXT NOT NULL COLLATE NOCASE,
                    dropLocation       TEXT NOT NULL COLLATE NOCASE,
                    quantityKg         INTEGER NOT NULL,
                    assignedDriver     TEXT COLLATE NOCASE,
                    status             TEXT NOT NULL,
                    signatureConfirmed INTEGER NOT NULL DEFAULT 0,
                    FOREIGN KEY (linkedRequestId) REFERENCES stock_requests(id),
                    FOREIGN KEY (assignedDriver)  REFERENCES users(username) ON DELETE SET NULL
                )""");

            st.execute("""
                CREATE TABLE IF NOT EXISTS activity_logs (
                    log_id        INTEGER PRIMARY KEY AUTOINCREMENT,
                    username      TEXT COLLATE NOCASE,
                    action        TEXT NOT NULL,
                    description   TEXT NOT NULL,
                    log_timestamp TEXT NOT NULL DEFAULT (datetime('now','localtime')),
                    FOREIGN KEY (username) REFERENCES users(username) ON DELETE SET NULL
                )""");

        } catch (SQLException e) {
            throw new DatabaseException("Could not create database tables", e);
        }
    }

    // Pre-seeded SUPER_ADMIN (System Boss / Admin / Overseer) -- only inserted if missing.
    private void seedSuperAdmin() {
        String sql = "INSERT OR IGNORE INTO users (username, password, fullName, role, status, branchName) " +
                "VALUES (?, ?, ?, ?, ?, NULL)";
        try (PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement(sql)) {
            ps.setString(1, "admin");
            ps.setString(2, "admin123");
            ps.setString(3, "System Administrator");
            ps.setString(4, "SUPER_ADMIN");
            ps.setString(5, "APPROVED");
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Could not seed admin account", e);
        }
    }
}