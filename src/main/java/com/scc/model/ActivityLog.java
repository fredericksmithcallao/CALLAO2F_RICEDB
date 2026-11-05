package com.scc.model;

/** One audit-log entry. userId is null when the user who did the action has since been deleted. */
public record ActivityLog(int logId, Integer userId, String actionType,
                          String description, String timestamp) {

    @Override
    public String toString() {
        return "[" + timestamp + "] #" + logId + " | User " + (userId == null ? "-" : userId) +
                " | " + actionType + " | " + description;
    }
}