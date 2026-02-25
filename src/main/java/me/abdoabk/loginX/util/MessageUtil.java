package me.abdoabk.loginX.util;

import org.bukkit.entity.Player;

public final class MessageUtil {

    private MessageUtil() {}

    public static void send(Player player, String message) {
        if (player != null && player.isOnline()) {
            player.sendMessage(message);
        }
    }
}