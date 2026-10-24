package com.scc.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Handles the SQLite connection and its transaction helpers.
 * One shared connection is used (console app, single user at a time), which also makes
 * multi-step operations easy to wrap in a transaction (begin / commit / rollback).
 *
 * Database Connection Guardrail: SQLite ignores foreign keys unless they are switched on,
 * so "PRAGMA foreign_keys = ON;" is executed every time a new connection is initialized.
 * Table creation lives in DatabaseInitializer.
 */
public final class DatabaseConnection {

    private static final String URL = "jdbc:sqlite:riceDistribution.db";
    private static Connection connection;

    private DatabaseConnection() { }

    public static synchronized Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection(URL);
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA foreign_keys = ON");   // guardrail: enforce FKs on every connection
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
}