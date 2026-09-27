package com.furkangame.bxlogin.storage;

import java.util.UUID;

public interface Storage {
    void init();
    void close();

    // Returns the hashed password, or null if not registered
    String loadPassword(UUID uuid);

    // Saves user data (password, ip, timestamp)
    void saveUser(UUID uuid, String password, String ip, long lastLogin);

    // Deletes a user completely
    void deleteUser(UUID uuid);

    // Loads session data (IP and Last Login Time) for auto-login checks
    SessionData loadSession(UUID uuid);

    // Simple container for session data
    class SessionData {
        public final String ip;
        public final long lastLogin;

        public SessionData(String ip, long lastLogin) {
            this.ip = ip;
            this.lastLogin = lastLogin;
        }
    }
}