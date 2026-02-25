package me.abdoabk.loginX.auth;

import me.abdoabk.loginX.LoginX;
import me.abdoabk.loginX.config.ConfigService;
import me.abdoabk.loginX.config.Messages;
import me.abdoabk.loginX.fingerprint.FingerprintService;
import me.abdoabk.loginX.model.PlayerAccount;
import me.abdoabk.loginX.premium.PremiumState;
import me.abdoabk.loginX.security.AltLimitService;
import me.abdoabk.loginX.session.SessionService;
import me.abdoabk.loginX.storage.PlayerRepository;
import me.abdoabk.loginX.util.HashUtil;
import me.abdoabk.loginX.util.IpUtil;
import me.abdoabk.loginX.util.MessageUtil;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles the /register command flow end-to-end.
 *
 * <h2>Fixes</h2>
 * <ul>
 *   <li>Double-register guard now sends a sensible "registration in progress" message
 *       instead of the misleading "already logged in" message.</li>
 *   <li>Fingerprint is built on the main thread (safe for Paper reflection), not inside
 *       a thenRunAsync callback.</li>
 * </ul>
 *
 * @see me.abdoabk.loginX.command.RegisterCommand
 * @see AltLimitService
 * @see SessionService
 */
public class RegisterService {

    private final LoginX plugin;
    private final PlayerRepository playerRepository;
    private final SessionService sessionService;
    private final FingerprintService fingerprintService;
    private final AltLimitService altLimitService;
    private final LoginTimeoutService loginTimeoutService;
    private final PasswordPolicy passwordPolicy;
    private final ConfigService config;
    private final Messages messages;

    /** Guards against concurrent duplicate registrations from the same player. */
    private final Set<UUID> registering = ConcurrentHashMap.newKeySet();

    public RegisterService(LoginX plugin) {
        this.plugin              = plugin;
        this.playerRepository    = plugin.getPlayerRepository();
        this.sessionService      = plugin.getSessionService();
        this.fingerprintService  = plugin.getFingerprintService();
        this.altLimitService     = plugin.getAltLimitService();
        this.loginTimeoutService = plugin.getLoginTimeoutService();
        this.passwordPolicy      = plugin.getPasswordPolicy();
        this.config              = plugin.getConfigService();
        this.messages            = plugin.getMessages();
    }

    public void register(Player player, String password, String confirm) {
        UUID uuid = player.getUniqueId();

        if (sessionService.isLoggedIn(uuid)) {
            MessageUtil.send(player, messages.get("errors.already-logged-in"));
            return;
        }

        // FIX: was using "already-logged-in" — now uses a correct message
        if (!registering.add(uuid)) {
            MessageUtil.send(player, messages.get("errors.registration-in-progress"));
            return;
        }

        if (!passwordPolicy.meetsMinLength(password)) {
            registering.remove(uuid);
            MessageUtil.send(player, messages.get("errors.password-too-short",
                    "{min}", String.valueOf(config.getMinPasswordLength())));
            return;
        }

        if (!password.equals(confirm)) {
            registering.remove(uuid);
            MessageUtil.send(player, messages.get("errors.password-mismatch"));
            return;
        }

        String ip = IpUtil.getIp(player);

        altLimitService.isOverLimit(ip).thenAcceptAsync(overLimit -> {
            if (overLimit) {
                registering.remove(uuid);
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        MessageUtil.send(player, messages.get("errors.no-permission")));
                return;
            }

            playerRepository.findByUuid(uuid).thenAcceptAsync(existing -> {
                if (existing != null) {
                    registering.remove(uuid);
                    plugin.getServer().getScheduler().runTask(plugin, () ->
                            MessageUtil.send(player, messages.get("errors.already-logged-in")));
                    return;
                }

                String hash    = HashUtil.hashPassword(password);
                PlayerAccount account = new PlayerAccount(
                        uuid, player.getName(), hash, PremiumState.CRACKED, Instant.now());

                playerRepository.save(account).thenRunAsync(() ->
                        // FIX: build fingerprint on main thread, not in async chain
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            if (!player.isOnline()) { registering.remove(uuid); return; }
                            String fp = fingerprintService.buildFingerprint(player).getHash();
                            sessionService.createSession(uuid, ip, fp).thenRun(() ->
                                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                                        registering.remove(uuid);
                                        if (!player.isOnline()) return;
                                        loginTimeoutService.cancelTimeout(uuid);
                                        plugin.getRestrictListener().removeRestrictions(player);
                                        MessageUtil.send(player, messages.get("auth.register-success"));
                                    })
                            );
                        })
                );
            }, plugin.getAsyncExecutor());
        }, plugin.getAsyncExecutor());
    }
}
