package me.abdoabk.loginX.auth;

import me.abdoabk.loginX.LoginX;
import me.abdoabk.loginX.config.ConfigService;
import me.abdoabk.loginX.config.Messages;
import me.abdoabk.loginX.session.SessionService;
import me.abdoabk.loginX.storage.PlayerRepository;
import me.abdoabk.loginX.util.HashUtil;
import me.abdoabk.loginX.util.MessageUtil;
import org.bukkit.entity.Player;

public class ChangePasswordService {

    private final LoginX plugin;
    private final PlayerRepository playerRepository;
    private final SessionService sessionService;
    private final PasswordPolicy passwordPolicy;
    private final ConfigService config;
    private final Messages messages;

    public ChangePasswordService(LoginX plugin) {
        this.plugin           = plugin;
        this.playerRepository = plugin.getPlayerRepository();
        this.sessionService   = plugin.getSessionService();
        this.passwordPolicy   = plugin.getPasswordPolicy();
        this.config           = plugin.getConfigService();
        this.messages         = plugin.getMessages();
    }

    public void changePassword(Player player, String oldPassword,
                               String newPassword, String confirm) {
        if (!sessionService.isLoggedIn(player.getUniqueId())) {
            MessageUtil.send(player, messages.get("errors.not-logged-in"));
            return;
        }

        if (!newPassword.equals(confirm)) {
            MessageUtil.send(player, messages.get("errors.password-mismatch"));
            return;
        }

        if (!passwordPolicy.meetsMinLength(newPassword)) {
            MessageUtil.send(player, messages.get("errors.password-too-short",
                    "{min}", String.valueOf(config.getMinPasswordLength())));
            return;
        }

        playerRepository.findByUuid(player.getUniqueId()).thenAcceptAsync(account -> {
            if (account == null) {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        MessageUtil.send(player, messages.get("auth.change-pass-fail")));
                return;
            }

            if (!HashUtil.verifyPassword(oldPassword, account.getPasswordHash())) {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        MessageUtil.send(player, messages.get("errors.wrong-password")));
                return;
            }

            if (HashUtil.verifyPassword(newPassword, account.getPasswordHash())) {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        MessageUtil.send(player, messages.get("errors.same-password")));
                return;
            }

            account.setPasswordHash(HashUtil.hashPassword(newPassword));
            playerRepository.save(account).thenRunAsync(() ->
                    sessionService.invalidate(player.getUniqueId()).thenRun(() ->
                            plugin.getServer().getScheduler().runTask(plugin, () -> {
                                if (!player.isOnline()) return;
                                MessageUtil.send(player, messages.get("auth.change-pass-success"));
                                MessageUtil.send(player, messages.get("info.login-required"));
                                plugin.getRestrictListener().applyRestrictions(player);
                                plugin.getLoginTimeoutService().startTimeout(player);
                            })
                    ), plugin.getAsyncExecutor()
            );
        }, plugin.getAsyncExecutor());
    }
}
