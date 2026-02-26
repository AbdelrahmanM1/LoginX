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

    public void handleJoin(Player player) {
        // Build fingerprint on main thread — Paper reflection is only safe here
        String ip        = IpUtil.getIp(player);
        String currentFp = fingerprintService.buildFingerprint(player).getHash();

        playerRepository.findByUuid(player.getUniqueId()).thenAcceptAsync(account -> {

            // No account → player must register
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

            // Account found → try session restore
            sessionService.getSession(player.getUniqueId()).thenAcceptAsync(session -> {
                SessionValidator.ValidationResult result =
                        sessionValidator.validate(session, account, ip, currentFp);

                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;

                    switch (result) {
                        case VALID -> {
                            sessionService.setLoggedIn(player.getUniqueId(), true);
                            if (config.isRollingSession()) {
                                sessionService.updateExpiry(player.getUniqueId());
                            }
                            plugin.getRestrictListener().removeRestrictions(player);
                            MessageUtil.send(player, messages.get("auth.session-restored"));
                        }
                        case FINGERPRINT_MISMATCH -> {
                            fingerprintService.recordChange(player.getUniqueId());
                            MessageUtil.send(player, messages.get("fingerprint.mismatch-warning"));
                            MessageUtil.send(player, messages.get("info.login-required"));
                            loginTimeoutService.startTimeout(player);
                        }
                        default -> {
                            MessageUtil.send(player, messages.get("info.login-required"));
                            loginTimeoutService.startTimeout(player);
                        }
                    }
                });
            });
        });
    }

    public void handleQuit(Player player) {
        fingerprintService.remove(player.getUniqueId());
        sessionService.setLoggedIn(player.getUniqueId(), false);
        loginTimeoutService.onQuit(player.getUniqueId());
        plugin.getRestrictListener().removeRestrictions(player);
    }
}
