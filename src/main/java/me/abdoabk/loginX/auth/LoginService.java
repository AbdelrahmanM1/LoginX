package me.abdoabk.loginX.auth;

import me.abdoabk.loginX.LoginX;
import me.abdoabk.loginX.config.ConfigService;
import me.abdoabk.loginX.config.Messages;
import me.abdoabk.loginX.fingerprint.FingerprintService;
import me.abdoabk.loginX.security.BruteForceService;
import me.abdoabk.loginX.session.SessionService;
import me.abdoabk.loginX.storage.PlayerRepository;
import me.abdoabk.loginX.util.HashUtil;
import me.abdoabk.loginX.util.IpUtil;
import me.abdoabk.loginX.util.MessageUtil;
import org.bukkit.entity.Player;

/**
 * Handles the /login command flow end-to-end.
 *
 * <h2>Fix: KICK_AFTER_ATTEMPTS aligned with config</h2>
 * The old code had {@code KICK_AFTER_ATTEMPTS = 4} hardcoded, ignoring the config value
 * {@code security.brute-force.max-attempts = 5}. Now reads directly from config so they
 * are always in sync.
 *
 * <h2>Fix: remaining attempts can't go negative</h2>
 * {@code remaining = max(0, maxAttempts - attempts)}.
 *
 * @see me.abdoabk.loginX.command.LoginCommand
 * @see BruteForceService
 * @see SessionService
 */
public class LoginService {

    private final LoginX plugin;
    private final PlayerRepository playerRepository;
    private final SessionService sessionService;
    private final FingerprintService fingerprintService;
    private final BruteForceService bruteForceService;
    private final LoginTimeoutService loginTimeoutService;
    private final ConfigService config;
    private final Messages messages;

    public LoginService(LoginX plugin) {
        this.plugin              = plugin;
        this.playerRepository    = plugin.getPlayerRepository();
        this.sessionService      = plugin.getSessionService();
        this.fingerprintService  = plugin.getFingerprintService();
        this.bruteForceService   = plugin.getBruteForceService();
        this.loginTimeoutService = plugin.getLoginTimeoutService();
        this.config              = plugin.getConfigService();
        this.messages            = plugin.getMessages();
    }

    public void login(Player player, String password) {
        if (sessionService.isLoggedIn(player.getUniqueId())) {
            MessageUtil.send(player, messages.get("errors.already-logged-in"));
            return;
        }

        String ip = IpUtil.getIp(player);
        int maxAttempts = config.getBruteForceMaxAttempts();

        // Run the entire login flow on our dedicated async executor
        plugin.getAsyncExecutor().execute(() -> {

            if (bruteForceService.isBanned(ip)) {
                long seconds = bruteForceService.getBanRemainingSeconds(ip);
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        player.kickPlayer(messages.getRaw("errors.too-many-attempts-kick")
                                .replace("{seconds}", String.valueOf(seconds))));
                return;
            }

            playerRepository.findByUuid(player.getUniqueId()).thenAcceptAsync(account -> {
                if (account == null) {
                    plugin.getServer().getScheduler().runTask(plugin, () ->
                            MessageUtil.send(player, messages.get("info.register-required")));
                    return;
                }

                // ── 3. Verify password ─────────────────────────────────────────────
                if (!HashUtil.verifyPassword(password, account.getPasswordHash())) {
                    int attempts  = bruteForceService.recordFailure(ip, player.getUniqueId());
                    int remaining = Math.max(0, maxAttempts - attempts);

                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (!player.isOnline()) return;
                        if (remaining == 0) {
                            // IP is now banned — bruteForceService.recordFailure() already wrote the DB row
                            long banSeconds = config.getBruteForceTempBanMinutes() * 60L;
                            player.kickPlayer(messages.getRaw("errors.too-many-attempts-kick")
                                    .replace("{seconds}", String.valueOf(banSeconds)));
                        } else {
                            MessageUtil.send(player, messages.get("errors.wrong-password-attempts")
                                    .replace("{remaining}", String.valueOf(remaining)));
                        }
                    });
                    return;
                }

                bruteForceService.clearAttempts(ip, player.getUniqueId());

                // Build fingerprint on main thread (Paper reflection)
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;
                    String fp = fingerprintService.buildFingerprint(player).getHash();

                    sessionService.createSession(player.getUniqueId(), ip, fp).thenRun(() ->
                            plugin.getServer().getScheduler().runTask(plugin, () -> {
                                if (!player.isOnline()) return;
                                loginTimeoutService.cancelTimeout(player.getUniqueId());
                                plugin.getRestrictListener().removeRestrictions(player);
                                MessageUtil.send(player, messages.get("auth.login-success"));
                            })
                    );
                });

            }, plugin.getAsyncExecutor());
        });
    }
}
