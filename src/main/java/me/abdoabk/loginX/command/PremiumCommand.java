package me.abdoabk.loginX.command;

import me.abdoabk.loginX.LoginX;
import me.abdoabk.loginX.premium.PremiumService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class PremiumCommand implements CommandExecutor {

    private final PremiumService premiumService;

    public PremiumCommand(@NotNull LoginX plugin) {
        this.premiumService = plugin.getPremiumService();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command is for players only.");
            return true;
        }

        premiumService.setPremium(player);
        return true;
    }
}