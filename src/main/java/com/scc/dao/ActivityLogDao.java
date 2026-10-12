package com.scc.dao;

import com.scc.model.ActivityLog;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class ActivityLogDao extends BaseDao {

    private static ActivityLog map(ResultSet rs) throws SQLException {
        return new ActivityLog(rs.getInt("log_id"), rs.getString("username"), rs.getString("action"),
                rs.getString("description"), rs.getString("log_timestamp"));
    }

    public void insert(String username, String action, String description) {
        insertRow("INSERT INTO activity_logs (username, action, description) VALUES (?, ?, ?)",
                username, action, description);
    }

    /** Newest first. */
    public List<ActivityLog> findAll() {
        return query("SELECT log_id, username, action, description, log_timestamp " +
                "FROM activity_logs ORDER BY log_id DESC", ActivityLogDao::map);
    }
}