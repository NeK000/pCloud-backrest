package com.example.pcloudbackrest;

enum Mode {
    BACKUP,
    RESTORE;

    static Mode parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("MODE is required and must be 'backup' or 'restore'.");
        }
        return switch (value.trim().toLowerCase()) {
            case "backup" -> BACKUP;
            case "restore" -> RESTORE;
            default -> throw new IllegalArgumentException("MODE must be 'backup' or 'restore', got '" + value + "'.");
        };
    }
}
