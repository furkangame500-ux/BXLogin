package com.furkangame.bxlogin.storage.implementation;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.furkangame.bxlogin.BXLogin;
import com.furkangame.bxlogin.storage.Storage;

import java.io.File;
import java.sql.*;
import java.util.UUID;

public class SQLStorage implements Storage {

    private final BXLogin plugin;
    private HikariDataSource dataSource;
    private final String type;

    public SQLStorage(BXLogin plugin, String type) {
        this.plugin = plugin;
        this.type = type;
    }

    @Override
    public void init() {
        HikariConfig config = new HikariConfig();
        String host = plugin.getConfig().getString("storage.host");
        int port = plugin.getConfig().getInt("storage.port");
        String db = plugin.getConfig().getString("storage.database");
        String user = plugin.getConfig().getString("storage.username");
        String pass = plugin.getConfig().getString("storage.password");
        boolean ssl = plugin.getConfig().getBoolean("storage.ssl");

        if (type.equals("MYSQL")) {
            config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + db + "?useSSL=" + ssl);
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");
            config.setUsername(user);
            config.setPassword(pass);
        } else if (type.equals("MARIADB")) {
            config.setJdbcUrl("jdbc:mariadb://" + host + ":" + port + "/" + db + "?useSSL=" + ssl);
            config.setDriverClassName("org.mariadb.jdbc.Driver");
            config.setUsername(user);
            config.setPassword(pass);
        } else {
            // SQLite
            File dbFile = new File(plugin.getDataFolder(), "database.db");
            config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
            config.setDriverClassName("org.sqlite.JDBC");
            config.setMaximumPoolSize(1);
        }

        if (!type.equals("SQLITE")) {
            config.setMaximumPoolSize(plugin.getConfig().getInt("storage.pool-size", 10));
        }

        this.dataSource = new HikariDataSource(config);
        createTable();
    }

    private void createTable() {
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            String autoInc = type.equals("SQLITE") ? "LONG" : "BIGINT";
            stmt.execute("CREATE TABLE IF NOT EXISTS users (" +
                    "uuid VARCHAR(36) PRIMARY KEY, " +
                    "password VARCHAR(255) NOT NULL, " +
                    "last_login " + autoInc + ", " +
                    "last_ip VARCHAR(64)" +
                    ");");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to create tables: " + e.getMessage());
        }
    }

    @Override
    public String loadPassword(UUID uuid) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT password FROM users WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("password");
        } catch (SQLException e) {
            plugin.getLogger().severe("SQL Load Error: " + e.getMessage());
        }
        return null;
    }

    @Override
    public SessionData loadSession(UUID uuid) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT last_ip, last_login FROM users WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new SessionData(rs.getString("last_ip"), rs.getLong("last_login"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void saveUser(UUID uuid, String password, String ip, long lastLogin) {
        // REPLACE INTO works on MySQL, MariaDB, and SQLite
        String sql = "REPLACE INTO users (uuid, password, last_ip, last_login) VALUES (?, ?, ?, ?)";
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, password);
            ps.setString(3, ip);
            ps.setLong(4, lastLogin);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("SQL Save Error: " + e.getMessage());
        }
    }

    @Override
    public void deleteUser(UUID uuid) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM users WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("SQL Delete Error: " + e.getMessage());
        }
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) dataSource.close();
    }
}