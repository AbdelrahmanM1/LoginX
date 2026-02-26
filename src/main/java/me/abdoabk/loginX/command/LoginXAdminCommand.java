package me.abdoabk.loginX.command;

import me.abdoabk.loginX.LoginX;
import me.abdoabk.loginX.config.Messages;
import me.abdoabk.loginX.premium.PremiumService;
import me.abdoabk.loginX.session.SessionService;
import me.abdoabk.loginX.storage.PlayerRepository;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class LoginXAdminCommand implements CommandExecutor, TabCompleter {

    private final LoginX plugin;
    private final SessionService sessionService;
    private final PremiumService premiumService;
    private final PlayerRepository playerRepository;
    private final Messages messages;

    public LoginXAdminCommand(LoginX plugin) {
        this.plugin = plugin;
        this.sessionService = plugin.getSessionService();
        this.premiumService = plugin.getPremiumService();
        this.playerRepository = plugin.getPlayerRepository();
        this.messages = plugin.getMessages();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("loginx.admin")) {
            sender.sendMessage(messages.get("errors.no-permission"));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                plugin.getConfigService().reload();
                plugin.getMessages().reload();
                sender.sendMessage(messages.get("admin.reload-success"));
            }

            case "info" -> {
                if (args.length < 2) { sender.sendMessage("/loginx info <player>"); return true; }
                String target = args[1];
                playerRepository.findByUsername(target).thenAcceptAsync(account -> {
                    if (account == null) {
                        plugin.getServer().getScheduler().runTask(plugin, () ->
                                sender.sendMessage(messages.get("admin.player-not-found", "{player}", target)));
                        return;
                    }
                    boolean loggedIn = sessionService.isLoggedIn(account.getUuid());
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        sender.sendMessage("§bPlayer: §f" + account.getUsername());
                        sender.sendMessage("§bUUID: §f" + account.getUuid());
                        sender.sendMessage("§bPremium State: §f" + account.getPremiumState());
                        sender.sendMessage("§bLogged In: §f" + loggedIn);
                    });
                });
            }

            case "session" -> {
                if (args.length < 3 || !args[1].equalsIgnoreCase("invalidate")) {
                    sender.sendMessage("/loginx session invalidate <player>");
                    return true;
                }
                String target = args[2];
                playerRepository.findByUsername(target).thenAcceptAsync(account -> {
                    if (account == null) {
                        sender.sendMessage(messages.get("admin.player-not-found", "{player}", target));
                        return;
                    }
                    sessionService.invalidate(account.getUuid()).thenRun(() ->
                            sender.sendMessage(messages.get("admin.session-invalidated", "{player}", target)));
                });
            }

            case "premium" -> {
                if (args.length < 3 || !args[1].equalsIgnoreCase("force")) {
                    sender.sendMessage("/loginx premium force <player>");
                    return true;
                }
                String target = args[2];
                premiumService.forcePremium(target);
                sender.sendMessage(messages.get("admin.premium-forced", "{player}", target));
            }

            case "unban" -> {
                if (args.length < 2) { sender.sendMessage("/loginx unban <ip>"); return true; }
                String ip = args[1];
                plugin.getBruteForceService().unbanIp(ip).thenRun(() ->
                        plugin.getServer().getScheduler().runTask(plugin, () ->
                                sender.sendMessage(messages.get("admin.ip-unbanned", "{ip}", ip))));
            }

            default -> sendHelp(sender);
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd,
                                                @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("loginx.admin")) return List.of();

        // /loginx <subcommand>
        if (args.length == 1) {
            List<String> subs = Arrays.asList("reload", "info", "session", "premium", "unban");
            return filter(subs, args[0]);
        }

        // /loginx session <invalidate>
        if (args.length == 2 && args[0].equalsIgnoreCase("session")) {
            return filter(List.of("invalidate"), args[1]);
        }

        // /loginx premium <force>
        if (args.length == 2 && args[0].equalsIgnoreCase("premium")) {
            return filter(List.of("force"), args[1]);
        }

        // /loginx info <player>
        // /loginx session invalidate <player>
        // /loginx premium force <player>
        boolean wantsPlayer =
                (args.length == 2 && args[0].equalsIgnoreCase("info")) ||
                        (args.length == 3 && args[0].equalsIgnoreCase("session") && args[1].equalsIgnoreCase("invalidate")) ||
                        (args.length == 3 && args[0].equalsIgnoreCase("premium") && args[1].equalsIgnoreCase("force"));

        if (wantsPlayer) {
            String typed = args[args.length - 1];
            return plugin.getServer().getOnlinePlayers().stream()
                    .map(p -> p.getName())
                    .filter(name -> name.toLowerCase().startsWith(typed.toLowerCase()))
                    .collect(Collectors.toList());
        }

        return List.of();
    }

    // Filters a list of options by what the player has typed so far
    private List<String> filter(List<String> options, String typed) {
        return options.stream()
                .filter(o -> o.toLowerCase().startsWith(typed.toLowerCase()))
                .collect(Collectors.toList());
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§b== LoginX Admin Commands ==");
        sender.sendMessage("§e/loginx reload §7- Reload config & messages");
        sender.sendMessage("§e/loginx info <player> §7- View account info");
        sender.sendMessage("§e/loginx session invalidate <player> §7- Invalidate session");
        sender.sendMessage("§e/loginx premium force <player> §7- Force premium lock");
        sender.sendMessage("§e/loginx unban <ip> §7- Unban an IP from brute-force lock");
    }
}
