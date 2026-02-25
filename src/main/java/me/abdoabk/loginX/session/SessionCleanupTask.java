package me.abdoabk.loginX.session;

import me.abdoabk.loginX.LoginX;
import org.bukkit.scheduler.BukkitRunnable;

public class SessionCleanupTask extends BukkitRunnable {

    private final LoginX plugin;
    private final SessionService sessionService;

    public SessionCleanupTask(LoginX plugin, SessionService sessionService) {
        this.plugin = plugin;
        this.sessionService = sessionService;
    }

    @Override
    public void run() {
        sessionService.cleanupExpired().exceptionally(e -> {
            plugin.getLogger().warning("Session cleanup failed: " + e.getMessage());
            return null;
        });
    }
}