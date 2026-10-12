package com.scc.dao;

/** Unchecked wrapper so upper layers don't need to deal with SQLException everywhere. */
public class DatabaseException extends RuntimeException {
    public DatabaseException(String message, Throwable cause) {
        super(message, cause);
    }
}