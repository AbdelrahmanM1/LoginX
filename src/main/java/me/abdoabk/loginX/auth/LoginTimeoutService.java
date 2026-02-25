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

/**
 * Manages per-player login timeout timers.
 *
 * <p>When a player joins and is NOT auto-authenticated, {@link #startTimeout(Player)}
 * schedules a kick if they do not authenticate within
 * {@code auth.login-timeout-seconds} (config).</p>
 *
 * <h2>Lifecycle</h2>
 * <ul>
 *   <li>Started by: {@link AuthService#handleJoin}, {@link me.abdoabk.loginX.command.LogoutCommand}</li>
 *   <li>Cancelled by: {@link LoginService#login}, {@link RegisterService#register}</li>
 *   <li>Cleaned up by: {@link AuthService#handleQuit} (via {@link #onQuit})</li>
 * </ul>
 *
 * @see ConfigService#getLoginTimeoutSeconds()
 * @see SessionService#isLoggedIn(UUID)
 */
public class LoginTimeoutService {

    private final LoginX plugin;
    private final SessionService sessionService;
    private final ConfigService config;
    private final Messages messages;

    /** UUID → pending kick task */
    private final Map<UUID, BukkitTask> timeoutTasks = new ConcurrentHashMap<>();

    public LoginTimeoutService(LoginX plugin) {
        this.plugin           = plugin;
        this.sessionService   = plugin.getSessionService();
        this.config           = plugin.getConfigService();
        this.messages         = plugin.getMessages();
    }

    /**
     * Starts (or restarts) the kick timer for {@code player}.
     * Any existing timer for this player is cancelled first.
     *
     * <p>Must be called from the <b>main server thread</b>.</p>
     *
     * @param player the player who must authenticate before the timer fires
     */
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

    /**
     * Cancels the pending timeout for {@code uuid}.
     * Called on successful login/register.
     */
    public void cancelTimeout(UUID uuid) {
        BukkitTask task = timeoutTasks.remove(uuid);
        if (task != null) { try { task.cancel(); } catch (Exception ignored) {} }
    }

    /**
     * Alias for {@link #cancelTimeout(UUID)} — called on player quit
     * to prevent a dangling task from firing after the player has left.
     */
    public void onQuit(UUID uuid) {
        cancelTimeout(uuid);
    }
}
