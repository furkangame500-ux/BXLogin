package com.furkangame.bxlogin.commands;

import com.furkangame.bxlogin.BXLogin;
import com.furkangame.bxlogin.managers.AuthManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class PlayerCommands implements CommandExecutor {

    private final BXLogin plugin;
    private final AuthManager authManager;

    public PlayerCommands(BXLogin plugin, AuthManager authManager) {
        this.plugin = plugin;
        this.authManager = authManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessage("error.not-player"));
            return true;
        }

        String name = command.getName().toLowerCase();

        if (name.equals("register")) {
            if (authManager.isLoggedIn(player.getUniqueId())) {
                player.sendMessage(plugin.getMessage("error.already-logged-in"));
                return true;
            }
            if (authManager.isRegistered(player.getUniqueId())) {
                player.sendMessage(plugin.getMessage("error.already-registered"));
                return true;
            }
            if (args.length != 2) {
                player.sendMessage(plugin.getMessage("usage.register"));
                return true;
            }
            if (!args[0].equals(args[1])) {
                player.sendMessage(plugin.getMessage("error.password-mismatch"));
                return true;
            }
            authManager.register(player, args[0]);
            player.sendMessage(plugin.getMessage("success.register"));
            return true;
        }

        if (name.equals("login")) {
            if (authManager.isLoggedIn(player.getUniqueId())) {
                player.sendMessage(plugin.getMessage("error.already-logged-in"));
                return true;
            }
            if (!authManager.isRegistered(player.getUniqueId())) {
                player.sendMessage(plugin.getMessage("error.not-registered"));
                return true;
            }
            if (args.length != 1) {
                player.sendMessage(plugin.getMessage("usage.login"));
                return true;
            }

            if (authManager.login(player, args[0])) {
                player.sendMessage(plugin.getMessage("success.login"));
            } else {
                player.sendMessage(plugin.getMessage("error.incorrect-password"));
            }
            return true;
        }

        if (name.equals("sifre")) {
            if (!authManager.isLoggedIn(player.getUniqueId())) {
                player.sendMessage(plugin.getMessage("error.not-authenticated"));
                return true;
            }
            if (args.length != 3 || !args[0].equalsIgnoreCase("değiştir")) {
                player.sendMessage(plugin.getMessage("usage.changepassword"));
                return true;
            }
            if (!args[1].equals(args[2])) {
                player.sendMessage(plugin.getMessage("error.password-mismatch"));
                return true;
            }
            authManager.changePassword(player, args[1]);
            return true;
        }

        return true;
    }
}
