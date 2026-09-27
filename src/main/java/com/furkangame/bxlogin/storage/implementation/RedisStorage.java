package com.furkangame.bxlogin.storage.implementation;

import com.furkangame.bxlogin.BXLogin;
import com.furkangame.bxlogin.storage.Storage;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.Map;
import java.util.UUID;

public class RedisStorage implements Storage {

    private final BXLogin plugin;
    private JedisPool jedisPool;

    public RedisStorage(BXLogin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        String host = plugin.getConfig().getString("storage.host");
        int port = plugin.getConfig().getInt("storage.port", 6379);
        String user = plugin.getConfig().getString("storage.username");
        String pass = plugin.getConfig().getString("storage.password");

        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(plugin.getConfig().getInt("storage.pool-size", 10));

        if (user != null && !user.isEmpty()) {
            this.jedisPool = new JedisPool(poolConfig, host, port, 2000, pass, user);
        } else if (pass != null && !pass.isEmpty()) {
            this.jedisPool = new JedisPool(poolConfig, host, port, 2000, pass);
        } else {
            this.jedisPool = new JedisPool(poolConfig, host, port);
        }
    }

    @Override
    public String loadPassword(UUID uuid) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.hget("bxlogin:user:" + uuid, "password");
        }
    }

    @Override
    public SessionData loadSession(UUID uuid) {
        try (Jedis jedis = jedisPool.getResource()) {
            Map<String, String> data = jedis.hgetAll("bxlogin:user:" + uuid);
            String ip = data.get("last_ip");
            String lastLoginStr = data.get("last_login");
            long lastLogin = (lastLoginStr != null) ? Long.parseLong(lastLoginStr) : 0;

            if (ip != null) {
                return new SessionData(ip, lastLogin);
            }
        }
        return null;
    }

    @Override
    public void saveUser(UUID uuid, String password, String ip, long lastLogin) {
        try (Jedis jedis = jedisPool.getResource()) {
            String key = "bxlogin:user:" + uuid;
            // If password/ip is null, we usually don't want to delete the key, just skip updating it.
            // But for simplicity in this method, we expect valid data or handle nulls carefully.
            if (password != null) jedis.hset(key, "password", password);
            if (ip != null) jedis.hset(key, "last_ip", ip);
            else jedis.hdel(key, "last_ip"); // If IP is null, remove it (session clear)

            jedis.hset(key, "last_login", String.valueOf(lastLogin));
        }
    }

    @Override
    public void deleteUser(UUID uuid) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.del("bxlogin:user:" + uuid);
        }
    }

    @Override
    public void close() {
        if (jedisPool != null && !jedisPool.isClosed()) jedisPool.close();
    }
}