package com.scc.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Handles the SQLite connection, table creation and the pre-seeded SUPER_ADMIN account.
 * One shared connection is used (console app, single user at a time), which also makes
 * multi-step operations easy to wrap in a transaction (begin / commit / rollback).
 */
public final class DatabaseConnection {

    private static final String URL = "jdbc:sqlite:riceDistribution.db";
    private static Connection connection;

    private DatabaseConnection() { }

    public static synchronized Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection(URL);
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA foreign_keys = ON");
            }
        }
        return connection;
    }

    public static void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException ignored) { }
    }

    /* ---------------------------- TRANSACTION HELPERS ---------------------------- */

    public static void begin() {
        try { getConnection().setAutoCommit(false); }
        catch (SQLException e) { throw new DatabaseException("Could not start transaction", e); }
    }

    public static void commit() {
        try {
            Connection c = getConnection();
            c.commit();
            c.setAutoCommit(true);
        } catch (SQLException e) { throw new DatabaseException("Could not commit transaction", e); }
    }

    public static void rollback() {
        try {
            Connection c = getConnection();
            c.rollback();
            c.setAutoCommit(true);
        } catch (SQLException e) { throw new DatabaseException("Could not roll back transaction", e); }
    }

    /* ---------------------------- SCHEMA + SEED DATA ---------------------------- */

    public static void initialize() {
        try (Statement st = getConnection().createStatement()) {

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
        seedSuperAdmin();
    }

    // Pre-seeded SUPER_ADMIN (System Boss / Admin / Overseer) -- only inserted if missing.
    private static void seedSuperAdmin() {
        String sql = "INSERT OR IGNORE INTO users (username, password, fullName, role, status, branchName) " +
                "VALUES (?, ?, ?, ?, ?, NULL)";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
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