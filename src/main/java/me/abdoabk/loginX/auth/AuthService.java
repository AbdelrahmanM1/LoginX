package me.abdoabk.loginX.auth;

import me.abdoabk.loginX.LoginX;
import me.abdoabk.loginX.config.ConfigService;
import me.abdoabk.loginX.config.Messages;
import me.abdoabk.loginX.fingerprint.FingerprintService;
import me.abdoabk.loginX.session.SessionService;
import me.abdoabk.loginX.session.SessionValidator;
import me.abdoabk.loginX.storage.PlayerRepository;
import me.abdoabk.loginX.util.IpUtil;
import me.abdoabk.loginX.util.MessageUtil;
import org.bukkit.entity.Player;

/**
 * Orchestrates the player join and quit authentication flow.
 *
 * <h2>handleJoin flow</h2>
 * <ol>
 *   <li>Build fingerprint on main thread (safe for Paper reflection).</li>
 *   <li>Async: look up account by UUID.</li>
 *   <li>No account → check for premium-name conflict → start timeout + prompt register.</li>
 *   <li>Account found → async: load session → validate.</li>
 *   <li>VALID → setLoggedIn + <b>removeRestrictions</b> (was missing — critical blindness bug fix).</li>
 *   <li>Otherwise → start timeout + prompt login.</li>
 * </ol>
 *
 * @see me.abdoabk.loginX.listener.PlayerJoinListener
 * @see me.abdoabk.loginX.listener.PlayerQuitListener
 */
public class AuthService {

    private final LoginX plugin;
    private final PlayerRepository playerRepository;
    private final SessionService sessionService;
    private final SessionValidator sessionValidator;
    private final FingerprintService fingerprintService;
    private final LoginTimeoutService loginTimeoutService;
    private final ConfigService config;
    private final Messages messages;

    public AuthService(LoginX plugin) {
        this.plugin              = plugin;
        this.playerRepository    = plugin.getPlayerRepository();
        this.sessionService      = plugin.getSessionService();
        this.sessionValidator    = plugin.getSessionValidator();
        this.fingerprintService  = plugin.getFingerprintService();
        this.loginTimeoutService = plugin.getLoginTimeoutService();
        this.config              = plugin.getConfigService();
        this.messages            = plugin.getMessages();
    }

    /**
     * Called from {@link me.abdoabk.loginX.listener.PlayerJoinListener} on the main thread
     * after the 20-tick client-load delay.
     */
    public void handleJoin(Player player) {
        // ── Build fingerprint NOW on main thread (Paper reflection is main-thread-safe) ──
        String ip        = IpUtil.getIp(player);
        String currentFp = fingerprintService.buildFingerprint(player).getHash();

        playerRepository.findByUuid(player.getUniqueId()).thenAcceptAsync(account -> {

            // ── No account: player must register ──────────────────────────────────
            if (account == null) {
                playerRepository.findByUsername(player.getName()).thenAcceptAsync(namedAccount -> {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (!player.isOnline()) return;
                        if (namedAccount != null && namedAccount.isPremiumLocked()) {
                            player.kickPlayer(messages.getRaw("premium.cracked-kick"));
                            return;
                        }
                        MessageUtil.send(player, messages.get("info.register-required"));
                        loginTimeoutService.startTimeout(player);
                    });
                });
                return;
            }

            // ── Account found: try session restore ────────────────────────────────
            sessionService.getSession(player.getUniqueId()).thenAcceptAsync(session -> {
                SessionValidator.ValidationResult result =
                        sessionValidator.validate(session, account, ip, currentFp);

                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;

                    switch (result) {
                        case VALID -> {
                            // ── FIX: was missing removeRestrictions() — caused permanent blindness ──
                            sessionService.setLoggedIn(player.getUniqueId(), true);
                            if (config.isRollingSession()) {
                                sessionService.updateExpiry(player.getUniqueId());
                            }
                            plugin.getRestrictListener().removeRestrictions(player);
                            MessageUtil.send(player, messages.get("auth.session-restored"));
                        }
                        case FINGERPRINT_MISMATCH -> {
                            // Record the drift event async, then prompt login
                            fingerprintService.recordChange(player.getUniqueId());
                            MessageUtil.send(player, messages.get("info.login-required"));
                            loginTimeoutService.startTimeout(player);
                        }
                        default -> {
                            // EXPIRED, IP_MISMATCH, NO_SESSION
                            MessageUtil.send(player, messages.get("info.login-required"));
                            loginTimeoutService.startTimeout(player);
                        }
                    }
                });
            });
        });
    }

    /**
     * Called from {@link me.abdoabk.loginX.listener.PlayerQuitListener}.
     * Cleans up all per-player state — fingerprint cache, login flag, pending timeout.
     */
    public void handleQuit(Player player) {
        fingerprintService.remove(player.getUniqueId());
        sessionService.setLoggedIn(player.getUniqueId(), false);
        loginTimeoutService.onQuit(player.getUniqueId());
        // Cancel any lingering restriction tasks (title/actionbar loops)
        plugin.getRestrictListener().removeRestrictions(player);
    }
}
