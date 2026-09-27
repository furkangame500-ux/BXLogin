package com.furkangame.bxlogin;

import com.furkangame.bxlogin.commands.PlayerCommands;
import com.furkangame.bxlogin.listeners.AuthListener;
import com.furkangame.bxlogin.managers.AuthManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BXLogin extends JavaPlugin {

    private static final int CONFIG_VERSION = 1;
    private static final long AUTH_TIMEOUT_MILLIS = 3 * 60 * 1000L;

    private AuthManager authManager;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private YamlConfiguration messages;
    private final Map<UUID, Long> authDeadlines = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> authBars = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("messages/messages.yml", false);
        loadMessages();

        if (getConfig().getInt("config-version") != CONFIG_VERSION) {
            getLogger().warning("Config sürümü güncel değil. Yeni config.yml oluşturmayı düşünün.");
        }

        this.authManager = new AuthManager(this);

        PlayerCommands playerCmds = new PlayerCommands(this, authManager);
        register("register", playerCmds);
        register("login", playerCmds);
        register("sifre", playerCmds);

        getServer().getPluginManager().registerEvents(new AuthListener(authManager, this), this);
        startAuthTimerTask();

        getLogger().info("BXLogin başarıyla etkinleştirildi.");
    }

    private void register(String command, PlayerCommands executor) {
        if (getCommand(command) != null) {
            getCommand(command).setExecutor(executor);
        }
    }

    public void loadMessages() {
        File file = new File(getDataFolder(), "messages/messages.yml");
        messages = YamlConfiguration.loadConfiguration(file);
    }

    public Component getMessage(String key) {
        String msg = messages.getString(key);
        if (msg == null) return Component.text("Eksik mesaj: " + key);
        return mm.deserialize(msg);
    }

    public Component getMessage(String key, String placeholder, String value) {
        String msg = messages.getString(key);
        if (msg == null) return Component.text("Eksik mesaj: " + key);
        return mm.deserialize(msg, Placeholder.parsed(placeholder, value));
    }

    public void startAuthTimer(Player player) {
        removeAuthTimer(player.getUniqueId());
        UUID uuid = player.getUniqueId();
        long deadline = System.currentTimeMillis() + AUTH_TIMEOUT_MILLIS;
        authDeadlines.put(uuid, deadline);

        BossBar bar = Bukkit.createBossBar("", BarColor.BLUE, BarStyle.SOLID);
        bar.setVisible(true);
        bar.addPlayer(player);
        authBars.put(uuid, bar);
        updateBossBar(player, deadline, bar);
    }

    public void removeAuthTimer(UUID uuid) {
        authDeadlines.remove(uuid);
        BossBar bar = authBars.remove(uuid);
        if (bar != null) {
            bar.removeAll();
            bar.setVisible(false);
        }
    }

    private void startAuthTimerTask() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            long now = System.currentTimeMillis();
            for (Map.Entry<UUID, Long> entry : authDeadlines.entrySet()) {
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player == null) {
                    removeAuthTimer(entry.getKey());
                    continue;
                }
                if (authManager.isLoggedIn(player.getUniqueId())) {
                    removeAuthTimer(player.getUniqueId());
                    continue;
                }

                long remaining = entry.getValue() - now;
                BossBar bar = authBars.get(player.getUniqueId());
                if (remaining <= 0) {
                    removeAuthTimer(player.getUniqueId());
                    player.kick(getMessage("error.timeout"));
                    continue;
                }
                if (bar != null) updateBossBar(player, entry.getValue(), bar);
            }
        }, 20L, 20L);
    }

    private void updateBossBar(Player player, long deadline, BossBar bar) {
        long remaining = Math.max(0L, deadline - System.currentTimeMillis());
        long total = AUTH_TIMEOUT_MILLIS;
        double progress = Math.max(0.0D, Math.min(1.0D, remaining / (double) total));
        long totalSeconds = (remaining + 999L) / 1000L;
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        String time = String.format(Locale.ROOT, "%02d:%02d", minutes, seconds);

        String key = authManager.isRegistered(player.getUniqueId()) ? "bossbar.login" : "bossbar.register";
        String raw = messages.getString(key, "<yellow>Giriş yap: <white>" + time);
        raw = raw.replace("{time}", time);
        bar.setTitle(LegacyComponentSerializer.legacySection().serialize(mm.deserialize(raw)));
        bar.setProgress(progress);
    }

    @Override
    public void onDisable() {
        if (authManager != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                authManager.restoreInventory(player);
                removeAuthTimer(player.getUniqueId());
            }
            authManager.close();
        }
    }

    public AuthManager getAuthManager() {
        return authManager;
    }
}
