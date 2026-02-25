package me.abdoabk.loginX.command;

import me.abdoabk.loginX.LoginX;
import me.abdoabk.loginX.auth.LoginService;
import me.abdoabk.loginX.config.Messages;
import me.abdoabk.loginX.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class LoginCommand implements CommandExecutor {

    private final LoginService loginService;
    private final Messages messages;

    public LoginCommand(LoginX plugin) {
        this.loginService = plugin.getLoginService();
        this.messages = plugin.getMessages();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command is for players only.");
            return true;
        }

        if (args.length < 1) {
            MessageUtil.send(player, messages.get("info.usage-login"));
            return true;
        }

        loginService.login(player, args[0]);
        return true;
    }
}