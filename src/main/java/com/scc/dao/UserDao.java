package com.scc.dao;

import com.scc.model.AccountStatus;
import com.scc.model.Role;
import com.scc.model.User;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class UserDao extends BaseDao {

    private static final String COLUMNS = "id, username, password, fullName, role, status, branchName";

    private static User map(ResultSet rs) throws SQLException {
        return new User(rs.getInt("id"), rs.getString("username"), rs.getString("password"),
                rs.getString("fullName"), Role.valueOf(rs.getString("role")),
                AccountStatus.valueOf(rs.getString("status")), rs.getString("branchName"));
    }

    public User insert(User u) {
        int id = insertRow("INSERT INTO users (username, password, fullName, role, status, branchName) " +
                        "VALUES (?, ?, ?, ?, ?, ?)",
                u.username(), u.password(), u.fullName(), u.role().name(), u.status().name(), u.branchName());
        return new User(id, u.username(), u.password(), u.fullName(), u.role(), u.status(), u.branchName());
    }

    /** Login check. Username and password are both case-sensitive here (same as the original code). */
    public User authenticate(String username, String password) {
        return queryOne("SELECT " + COLUMNS + " FROM users WHERE username = ? COLLATE BINARY AND password = ?",
                UserDao::map, username, password);
    }

    /** Username lookup is case-insensitive (same as the original equalsIgnoreCase). */
    public User findByUsername(String username) {
        return queryOne("SELECT " + COLUMNS + " FROM users WHERE username = ?", UserDao::map, username);
    }

    public List<User> findAll() {
        return query("SELECT " + COLUMNS + " FROM users ORDER BY id", UserDao::map);
    }

    public List<User> findByStatus(AccountStatus status) {
        return query("SELECT " + COLUMNS + " FROM users WHERE status = ? ORDER BY id",
                UserDao::map, status.name());
    }

    public void updateStatus(String username, AccountStatus status) {
        updateRows("UPDATE users SET status = ? WHERE username = ?", status.name(), username);
    }

    public void delete(String username) {
        updateRows("DELETE FROM users WHERE username = ?", username);
    }
}