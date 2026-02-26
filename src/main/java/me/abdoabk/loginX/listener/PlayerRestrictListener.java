package me.abdoabk.loginX.listener;

import me.abdoabk.loginX.LoginX;
import me.abdoabk.loginX.config.Messages;
import me.abdoabk.loginX.session.SessionService;
import me.abdoabk.loginX.storage.PlayerRepository;
import me.abdoabk.loginX.util.MessageUtil;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerRestrictListener implements Listener {

    private static final int BLINDNESS_TICKS = 6 * 60 * 20;

    private final LoginX plugin;
    private final SessionService sessionService;
    private final PlayerRepository playerRepository;
    private final Messages messages;

    private final Map<UUID, BukkitTask> actionBarTasks = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> titleTasks = new ConcurrentHashMap<>();

    public PlayerRestrictListener(LoginX plugin) {
        this.plugin = plugin;
        this.sessionService = plugin.getSessionService();
        this.playerRepository = plugin.getPlayerRepository();
        this.messages = plugin.getMessages();
    }

    public void applyRestrictions(Player player) {
        UUID uuid = player.getUniqueId();

        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, BLINDNESS_TICKS, 0, false, false));

        playerRepository.findByUuid(uuid).thenAcceptAsync(account ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline() || sessionService.isLoggedIn(uuid)) return;

                    boolean unregistered = account == null;
                    String title = unregistered ? messages.getRaw("restrict.title-not-registered")
                            : messages.getRaw("restrict.title-not-logged-in");
                    String subtitle = unregistered ? messages.getRaw("restrict.subtitle-register")
                            : messages.getRaw("restrict.subtitle-login");
                    Sound sound = unregistered ? Sound.BLOCK_NOTE_BLOCK_BASS : Sound.BLOCK_NOTE_BLOCK_PLING;

                    startTitleLoop(player, title, subtitle, sound);
                    startActionBarLoop(player, unregistered);
                })
        );
    }

    public void removeRestrictions(Player player) {
        UUID uuid = player.getUniqueId();
        cancelActionBarTask(uuid);
        cancelTitleTask(uuid);
        player.resetTitle();
        player.removePotionEffect(PotionEffectType.BLINDNESS);
    }

    private void startTitleLoop(Player player, String title, String subtitle, Sound sound) {
        cancelTitleTask(player.getUniqueId());
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || sessionService.isLoggedIn(player.getUniqueId())) {
                    cancel();
                    titleTasks.remove(player.getUniqueId());
                    return;
                }
                player.sendTitle(title, subtitle, 10, 70, 10);
                player.playSound(player.getLocation(), sound, 0.8f, 1.0f);
            }
        }.runTaskTimer(plugin, 0L, 60L);
        titleTasks.put(player.getUniqueId(), task);
    }

    private void startActionBarLoop(Player player, boolean unregistered) {
        cancelActionBarTask(player.getUniqueId());
        String msg = unregistered ? messages.getRaw("restrict.actionbar-register")
                : messages.getRaw("restrict.actionbar-login");
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || sessionService.isLoggedIn(player.getUniqueId())) {
                    cancel();
                    actionBarTasks.remove(player.getUniqueId());
                    return;
                }
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(msg));
            }
        }.runTaskTimer(plugin, 0L, 40L);
        actionBarTasks.put(player.getUniqueId(), task);
    }

    private void cancelTitleTask(UUID uuid) {
        BukkitTask t = titleTasks.remove(uuid);
        if (t != null) t.cancel();
    }

    private void cancelActionBarTask(UUID uuid) {
        BukkitTask t = actionBarTasks.remove(uuid);
        if (t != null) t.cancel();
    }

    private boolean isRestricted(Player player) {
        return !sessionService.isLoggedIn(player.getUniqueId())
                && !player.hasPermission("loginx.bypass");
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!isRestricted(player)) return;
        String cmd = event.getMessage().toLowerCase().split(" ")[0];

        if (cmd.equals("/login") || cmd.equals("/register")) return;

        event.setCancelled(true);
        MessageUtil.send(player, messages.get("errors.not-logged-in"));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        if (isRestricted(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!isRestricted(player)) return;

        var from = event.getFrom();
        var to = event.getTo();
        if (to != null && (from.getBlockX() != to.getBlockX()
                || from.getBlockY() != to.getBlockY()
                || from.getBlockZ() != to.getBlockZ())) {
            event.setTo(from);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (isRestricted(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (isRestricted(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventory(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player && isRestricted(player))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && isRestricted(player))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (isRestricted(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (isRestricted(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPickup(PlayerAttemptPickupItemEvent event) {
        if (isRestricted(event.getPlayer())) event.setCancelled(true);
    }
}
