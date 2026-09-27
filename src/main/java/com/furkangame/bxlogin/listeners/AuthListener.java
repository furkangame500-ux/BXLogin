package com.furkangame.bxlogin.listeners;

import com.furkangame.bxlogin.BXLogin;
import com.furkangame.bxlogin.managers.AuthManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;

import java.util.Locale;

public class AuthListener implements Listener {

    private final AuthManager authManager;
    private final BXLogin plugin;

    public AuthListener(AuthManager authManager, BXLogin plugin) {
        this.authManager = authManager;
        this.plugin = plugin;
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        authManager.loadPlayerData(event.getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.removeAuthTimer(event.getPlayer().getUniqueId());
        authManager.restoreInventory(event.getPlayer());
        authManager.unloadPlayerData(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        authManager.hideInventory(event.getPlayer());

        if (authManager.isBedrockPlayer(event.getPlayer().getUniqueId())) {
            authManager.login(event.getPlayer());
            event.getPlayer().sendMessage(plugin.getMessage("join.bedrock-auto-login"));
            return;
        }

        if (plugin.getConfig().getBoolean("sessions.enabled", false) && authManager.trySessionLogin(event.getPlayer())) {
            return;
        }

        plugin.startAuthTimer(event.getPlayer());
        boolean registered = authManager.isRegistered(event.getPlayer().getUniqueId());
        event.getPlayer().sendMessage(plugin.getMessage(registered ? "join.login-needed" : "join.register-needed"));
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (authManager.isLoggedIn(event.getPlayer().getUniqueId())) return;

        String raw = event.getMessage().trim();
        if (raw.isEmpty() || !raw.startsWith("/")) return;
        String[] split = raw.substring(1).split("\\s+");
        if (split.length == 0) return;

        String command = split[0].toLowerCase(Locale.ROOT);
        int colon = command.indexOf(':');
        if (colon >= 0) command = command.substring(colon + 1);

        boolean allowed = command.equals("login") || command.equals("giriş") || command.equals("giris")
                || command.equals("register") || command.equals("kayıt") || command.equals("kayit")
                || command.equals("sifre") || command.equals("şifre");

        if (!allowed) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.getMessage("error.not-authenticated"));
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!authManager.isLoggedIn(event.getPlayer().getUniqueId()) && event.getTo() != null) {
            if (event.getFrom().getX() != event.getTo().getX() || event.getFrom().getZ() != event.getTo().getZ()) {
                event.setTo(event.getFrom());
            }
        }
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        if (!authManager.isLoggedIn(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.getMessage("error.not-authenticated"));
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (!authManager.isLoggedIn(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof org.bukkit.entity.Player player && !authManager.isLoggedIn(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof org.bukkit.entity.Player player && !authManager.isLoggedIn(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
