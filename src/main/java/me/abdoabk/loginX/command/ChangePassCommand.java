package me.abdoabk.loginX.command;

import me.abdoabk.loginX.LoginX;
import me.abdoabk.loginX.auth.ChangePasswordService;
import me.abdoabk.loginX.config.Messages;
import me.abdoabk.loginX.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class ChangePassCommand implements CommandExecutor {

    private final ChangePasswordService changePasswordService;
    private final Messages messages;

    public ChangePassCommand(LoginX plugin) {
        this.changePasswordService = plugin.getChangePasswordService();
        this.messages = plugin.getMessages();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command is for players only.");
            return true;
        }

        if (args.length < 3) {
            MessageUtil.send(player, messages.get("info.usage-changepass"));
            return true;
        }

        changePasswordService.changePassword(player, args[0], args[1], args[2]);
        return true;
    }
}