package com.furkangame.bxlogin.managers;

import com.furkangame.bxlogin.BXLogin;
import com.furkangame.bxlogin.storage.Storage;
import com.furkangame.bxlogin.storage.implementation.RedisStorage;
import com.furkangame.bxlogin.storage.implementation.SQLStorage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.geysermc.floodgate.api.FloodgateApi;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class AuthManager {

    private final BXLogin plugin;
    private Storage storage; // <--- The new Interface

    private final ConcurrentHashMap<UUID, String> credentials = new ConcurrentHashMap<>();
    private final Set<UUID> activeSessions = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private final Map<UUID, ItemStack[]> savedContents = new ConcurrentHashMap<>();
    private final Map<UUID, ItemStack[]> savedArmor = new ConcurrentHashMap<>();
    private final Map<UUID, ItemStack[]> savedExtra = new ConcurrentHashMap<>();

    public AuthManager(BXLogin plugin) {
        this.plugin = plugin;
        initStorage();
    }

    private void initStorage() {
        String type = plugin.getConfig().getString("storage.type", "SQLITE").toUpperCase();
        plugin.getLogger().info("Initializing storage: " + type);

        if (type.equals("REDIS")) {
            this.storage = new RedisStorage(plugin);
        } else {
            // Handles SQLITE, MYSQL, MARIADB
            this.storage = new SQLStorage(plugin, type);
        }
        this.storage.init();
    }

    // --- Loading / Unloading ---

    public void loadPlayerData(UUID uuid) {
        String pass = storage.loadPassword(uuid);
        if (pass != null) {
            credentials.put(uuid, pass);
        }
    }

    public void unloadPlayerData(UUID uuid) {
        credentials.remove(uuid);
        activeSessions.remove(uuid);
        savedContents.remove(uuid);
        savedArmor.remove(uuid);
        savedExtra.remove(uuid);
    }

    // --- Session Logic ---

    public boolean trySessionLogin(Player player) {
        if (!plugin.getConfig().getBoolean("sessions.enabled", true)) return false;

        UUID uuid = player.getUniqueId();
        Storage.SessionData session = storage.loadSession(uuid);

        if (session != null && session.ip != null) {
            String currentIp = player.getAddress().getAddress().getHostAddress();

            // 1. IP Check
            if (session.ip.equals(currentIp)) {

                // 2. Time Check
                long timeout = plugin.getConfig().getLong("sessions.timeout-minutes", 60);
                if (timeout > 0) {
                    long diff = TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - session.lastLogin);
                    if (diff > timeout) {
                        player.sendMessage(plugin.getMessage("error.session-expired"));
                        return false;
                    }
                }

                // Success
                login(player, false);
                player.sendMessage(plugin.getMessage("success.session-resumed"));
                return true;
            }
        }
        return false;
    }

    // --- Core Auth Logic ---

    public void register(Player player, String password) {
        String hashed = hash(password);
        credentials.put(player.getUniqueId(), hashed);

        asyncSave(player.getUniqueId(), hashed, player.getAddress().getAddress().getHostAddress());

        login(player);
        player.sendMessage(plugin.getMessage("success.register"));
    }

    public boolean login(Player player, String password) {
        String stored = credentials.get(player.getUniqueId());
        if (stored != null && stored.equals(hash(password))) {
            login(player);
            asyncSave(player.getUniqueId(), stored, player.getAddress().getAddress().getHostAddress());
            return true;
        }
        return false;
    }

    public void login(Player player) { login(player, true); }

    public void login(Player player, boolean effects) {
        activeSessions.add(player.getUniqueId());
        plugin.removeAuthTimer(player.getUniqueId());
        restoreInventory(player);
        if (effects) {
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
            player.showTitle(Title.title(plugin.getMessage("title.register-header"), plugin.getMessage("success.login")));
        }
    }

    public void logout(Player player) {
        activeSessions.remove(player.getUniqueId());
        hideInventory(player);
        if (plugin.getConfig().getBoolean("sessions.clear-on-logout", true)) {
            // Keep password, remove IP (pass null as IP)
            asyncSave(player.getUniqueId(), credentials.get(player.getUniqueId()), null);
        }
    }

    public void unregister(Player player) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> storage.deleteUser(player.getUniqueId()));
        unloadPlayerData(player.getUniqueId());
        restoreInventory(player);
        player.kick(plugin.getMessage("success.account-deleted"));
    }

    public void changePassword(Player player, String newPass) {
        String hashed = hash(newPass);
        credentials.put(player.getUniqueId(), hashed);
        asyncSave(player.getUniqueId(), hashed, player.getAddress().getAddress().getHostAddress());
        player.sendMessage(plugin.getMessage("success.password-changed"));
    }

    // --- Helpers ---

    private void asyncSave(UUID uuid, String pass, String ip) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            storage.saveUser(uuid, pass, ip, System.currentTimeMillis());
        });
    }

    public void hideInventory(Player p) {
        savedContents.put(p.getUniqueId(), p.getInventory().getContents());
        savedArmor.put(p.getUniqueId(), p.getInventory().getArmorContents());
        savedExtra.put(p.getUniqueId(), p.getInventory().getExtraContents());
        p.getInventory().clear();
        p.getInventory().setArmorContents(null);
        p.getInventory().setExtraContents(null);
    }

    public void restoreInventory(Player p) {
        if (savedContents.containsKey(p.getUniqueId())) {
            p.getInventory().setContents(savedContents.get(p.getUniqueId()));
            p.getInventory().setArmorContents(savedArmor.get(p.getUniqueId()));
            p.getInventory().setExtraContents(savedExtra.get(p.getUniqueId()));
            savedContents.remove(p.getUniqueId());
            savedArmor.remove(p.getUniqueId());
            savedExtra.remove(p.getUniqueId());
        }
    }

    public boolean isRegistered(UUID uuid) { return credentials.containsKey(uuid); }
    public boolean isLoggedIn(UUID uuid) { return activeSessions.contains(uuid); }
    public boolean isBedrockPlayer(UUID uuid) {
        return Bukkit.getPluginManager().getPlugin("Floodgate") != null && FloodgateApi.getInstance().isFloodgatePlayer(uuid);
    }
    private String hash(String raw) {
        try {
            MessageDigest d = MessageDigest.getInstance("SHA-256");
            byte[] h = d.digest(raw.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(h);
        } catch (NoSuchAlgorithmException e) { throw new RuntimeException(e); }
    }
    public void close() { if (storage != null) storage.close(); }
}