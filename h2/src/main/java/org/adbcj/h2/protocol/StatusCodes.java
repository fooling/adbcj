package org.adbcj.h2.protocol;

import org.adbcj.DbException;


public enum StatusCodes {
    STATUS_ERROR(0),
    STATUS_OK(1);

    private final int statusValue;

    StatusCodes(int statusValue) {
        this.statusValue = statusValue;
    }

    public boolean isStatus(int status) {
        return this.statusValue == status;
    }

    /**
     * Expect this status or throw
     */
    public void expectStatusOrThrow(int status) {
        if (!isStatus(status)) {
            throw new DbException("Expected status: " + this + " but got: " + status);
        }
    }
}
