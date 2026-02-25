package me.abdoabk.loginX.listener;

import me.abdoabk.loginX.LoginX;
import me.abdoabk.loginX.auth.AuthService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerQuitListener implements Listener {

    private final AuthService authService;

    public PlayerQuitListener(LoginX plugin) {
        this.authService = plugin.getAuthService();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        authService.handleQuit(event.getPlayer());
    }
}