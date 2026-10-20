package com.zjgsu.oes.model;

public enum TaskStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED;

    public boolean isActive() {
        return this == PENDING || this == RUNNING;
    }
}
