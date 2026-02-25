package me.abdoabk.loginX.listener;

import me.abdoabk.loginX.LoginX;
import me.abdoabk.loginX.auth.AuthService;
import me.abdoabk.loginX.premium.PremiumState;
import me.abdoabk.loginX.session.SessionService;
import me.abdoabk.loginX.storage.PlayerRepository;
import me.abdoabk.loginX.util.IpUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Entry point for the entire authentication flow on player join.
 *
 * <h2>Flow</h2>
 * <pre>
 * PlayerJoinEvent (main thread)
 *   ├─ has loginx.bypass? → setLoggedIn(true), done
 *   └─ applyRestrictions() immediately (blindness applied synchronously, no tick gap)
 *        └─ runTaskLater +20 ticks (let client finish loading)
 *             └─ findByUuid() async
 *                  ├─ account == PREMIUM_LOCKED → createSession() → removeRestrictions()
 *                  └─ otherwise → authService.handleJoin() on main thread
 * </pre>
 *
 * @see PlayerRestrictListener#applyRestrictions(Player)
 * @see AuthService#handleJoin(Player)
 */
public class PlayerJoinListener implements Listener {

    private final LoginX plugin;
    private final AuthService authService;
    private final SessionService sessionService;
    private final PlayerRepository playerRepository;

    public PlayerJoinListener(LoginX plugin) {
        this.plugin           = plugin;
        this.authService      = plugin.getAuthService();
        this.sessionService   = plugin.getSessionService();
        this.playerRepository = plugin.getPlayerRepository();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // ── Bypass: trusted admins skip auth entirely ──────────────────────────
        if (player.hasPermission("loginx.bypass")) {
            sessionService.setLoggedIn(player.getUniqueId(), true);
            return;
        }

        // ── Apply restrictions immediately (main thread, synchronous blindness) ─
        // applyRestrictions() adds blindness directly — no runTask() wrapper —
        // so there is zero tick gap where the player can see.
        plugin.getRestrictListener().applyRestrictions(player);

        // ── Wait one second for the client to finish loading before auth logic ──
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;

            playerRepository.findByUuid(player.getUniqueId()).thenAccept(account -> {
                // Premium-locked: auto-login — no password needed
                if (account != null && account.getPremiumState() == PremiumState.PREMIUM_LOCKED) {
                    String ip = IpUtil.getIp(player);
                    String fp = plugin.getFingerprintService().buildFingerprint(player).getHash();
                    sessionService.createSession(player.getUniqueId(), ip, fp).thenRun(() ->
                            plugin.getServer().getScheduler().runTask(plugin, () -> {
                                if (player.isOnline())
                                    plugin.getRestrictListener().removeRestrictions(player);
                            })
                    );
                    return;
                }

                // Cracked / unregistered: full auth flow
                plugin.getServer().getScheduler().runTask(plugin,
                        () -> authService.handleJoin(player));
            });
        }, 20L);
    }
}
