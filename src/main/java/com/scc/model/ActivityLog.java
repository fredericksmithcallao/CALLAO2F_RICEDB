package com.scc.model;

public record ActivityLog(int logId, String username, String action,
                          String description, String timestamp) {

    @Override
    public String toString() {
        return "[" + timestamp + "] #" + logId + " | " + (username == null ? "(deleted user)" : username) +
                " | " + action + " | " + description;
    }
}