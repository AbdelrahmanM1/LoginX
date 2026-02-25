package me.abdoabk.loginX.command;

import me.abdoabk.loginX.LoginX;
import me.abdoabk.loginX.config.Messages;
import me.abdoabk.loginX.session.SessionService;
import me.abdoabk.loginX.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class LogoutCommand implements CommandExecutor {

    private final LoginX plugin;
    private final SessionService sessionService;
    private final Messages messages;

    public LogoutCommand(LoginX plugin) {
        this.plugin = plugin;
        this.sessionService = plugin.getSessionService();
        this.messages = plugin.getMessages();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command is for players only.");
            return true;
        }

        if (!sessionService.isLoggedIn(player.getUniqueId())) {
            MessageUtil.send(player, messages.get("errors.not-logged-in"));
            return true;
        }

        sessionService.invalidate(player.getUniqueId()).thenRun(() ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    MessageUtil.send(player, messages.get("auth.logout-success"));
                    // Re-apply restrictions so the player must log back in
                    plugin.getRestrictListener().applyRestrictions(player);
                    plugin.getLoginTimeoutService().startTimeout(player);
                })
        );
        return true;
    }
}