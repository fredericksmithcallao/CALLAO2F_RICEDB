package com.scc.dao;

import com.scc.model.ActivityLog;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/** Inserts and fetches audit-log entries (activity_logs table). */
public class ActivityLogDao extends BaseDao {

    private static final String COLUMNS = "log_id, user_id, action_type, description, timestamp";

    private static ActivityLog map(ResultSet rs) throws SQLException {
        int uid = rs.getInt("user_id");
        Integer userId = rs.wasNull() ? null : uid;
        return new ActivityLog(rs.getInt("log_id"), userId, rs.getString("action_type"),
                rs.getString("description"), rs.getString("timestamp"));
    }

    /** Records one action. A userId of 0 or less is stored as NULL (no user). */
    public void logAction(int userId, String actionType, String description) {
        Integer uid = userId > 0 ? userId : null;
        insertRow("INSERT INTO activity_logs (user_id, action_type, description) VALUES (?, ?, ?)",
                uid, actionType, description);
    }

    /** Every log entry, newest first. */
    public List<ActivityLog> getAllLogs() {
        return query("SELECT " + COLUMNS + " FROM activity_logs ORDER BY timestamp DESC, log_id DESC",
                ActivityLogDao::map);
    }

    /** Log entries made by one user, newest first. */
    public List<ActivityLog> getLogsByUserId(int userId) {
        return query("SELECT " + COLUMNS + " FROM activity_logs WHERE user_id = ? ORDER BY timestamp DESC, log_id DESC",
                ActivityLogDao::map, userId);
    }

    /** Deletes all historical log entries. Returns how many were removed. */
    public int clearLogs() {
        return updateRows("DELETE FROM activity_logs");
    }
}