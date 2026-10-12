package com.scc.model;

/** Immutable user record. branchName is only used by STORE_MANAGER (null for other roles). */
public record User(int id, String username, String password, String fullName,
                   Role role, AccountStatus status, String branchName) {

    @Override
    public String toString() {
        return "Username: " + username + " | Name: " + fullName + " | Role: " + role +
                " | Branch: " + (branchName == null ? "N/A" : branchName) + " | Status: " + status;
    }
}