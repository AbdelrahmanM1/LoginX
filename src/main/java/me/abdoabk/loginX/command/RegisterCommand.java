package me.abdoabk.loginX.command;

import me.abdoabk.loginX.LoginX;
import me.abdoabk.loginX.auth.RegisterService;
import me.abdoabk.loginX.config.Messages;
import me.abdoabk.loginX.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class RegisterCommand implements CommandExecutor {

    private final RegisterService registerService;
    private final Messages messages;

    public RegisterCommand(LoginX plugin) {
        this.registerService = plugin.getRegisterService();
        this.messages = plugin.getMessages();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command is for players only.");
            return true;
        }

        if (args.length < 2) {
            MessageUtil.send(player, messages.get("info.usage-register"));
            return true;
        }

        registerService.register(player, args[0], args[1]);
        return true;
    }
}