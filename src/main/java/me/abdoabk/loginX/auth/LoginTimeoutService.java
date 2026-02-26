package me.abdoabk.loginX.auth;

import me.abdoabk.loginX.LoginX;
import me.abdoabk.loginX.config.ConfigService;
import me.abdoabk.loginX.config.Messages;
import me.abdoabk.loginX.session.SessionService;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LoginTimeoutService {

    private final LoginX plugin;
    private final SessionService sessionService;
    private final ConfigService config;
    private final Messages messages;

    private final Map<UUID, BukkitTask> timeoutTasks = new ConcurrentHashMap<>();

    public LoginTimeoutService(LoginX plugin) {
        this.plugin         = plugin;
        this.sessionService = plugin.getSessionService();
        this.config         = plugin.getConfigService();
        this.messages       = plugin.getMessages();
    }

    public void startTimeout(Player player) {
        cancelTimeout(player.getUniqueId());

        int  timeoutSeconds = config.getLoginTimeoutSeconds();
        long timeoutTicks   = timeoutSeconds * 20L;

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                timeoutTasks.remove(player.getUniqueId());
                if (!player.isOnline()) return;
                if (sessionService.isLoggedIn(player.getUniqueId())) return;

                String kickMsg = messages.getRaw("errors.login-timeout")
                        .replace("{seconds}", String.valueOf(timeoutSeconds));
                player.kickPlayer(kickMsg);
                plugin.getLogger().info("[LoginX] Kicked " + player.getName()
                        + " — did not authenticate within " + timeoutSeconds + "s.");
            }
        }.runTaskLater(plugin, timeoutTicks);

        timeoutTasks.put(player.getUniqueId(), task);
    }

    public void cancelTimeout(UUID uuid) {
        BukkitTask task = timeoutTasks.remove(uuid);
        if (task != null) { try { task.cancel(); } catch (Exception ignored) {} }
    }

    public void onQuit(UUID uuid) {
        cancelTimeout(uuid);
    }
}
