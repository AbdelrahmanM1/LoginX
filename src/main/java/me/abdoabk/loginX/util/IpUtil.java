package me.abdoabk.loginX.util;

import org.bukkit.entity.Player;

import java.net.InetSocketAddress;

public final class IpUtil {

    private IpUtil() {}

    public static String getIp(Player player) {
        InetSocketAddress address = player.getAddress();
        if (address == null) return "unknown";
        return address.getAddress().getHostAddress();
    }
}