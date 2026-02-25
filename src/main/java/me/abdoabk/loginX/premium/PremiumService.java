package me.abdoabk.loginX.premium;

import me.abdoabk.loginX.LoginX;
import me.abdoabk.loginX.config.Messages;
import me.abdoabk.loginX.model.PlayerAccount;
import me.abdoabk.loginX.session.SessionService;
import me.abdoabk.loginX.storage.PlayerRepository;
import me.abdoabk.loginX.util.MessageUtil;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.logging.Level;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 * Verifies Mojang premium status via the Mojang REST API and locks accounts.
 *
 * <h2>Fix: explicit executor in async chains</h2>
 * {@code thenAcceptAsync} without an executor uses the common ForkJoinPool, which
 * is shared JVM-wide and can cause interference. All async callbacks now use
 * {@code plugin.getAsyncExecutor()} explicitly.
 *
 * @see me.abdoabk.loginX.command.PremiumCommand
 * @see me.abdoabk.loginX.command.LoginXAdminCommand
 */
public class PremiumService {

    private static final String MOJANG_API = "https://api.mojang.com/users/profiles/minecraft/";
    private static final int RECONNECT_DELAY_SECONDS = 3;

    private final LoginX plugin;
    private final PlayerRepository playerRepository;
    private final SessionService sessionService;
    private final Messages messages;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public PremiumService(LoginX plugin) {
        this.plugin           = plugin;
        this.playerRepository = plugin.getPlayerRepository();
        this.sessionService   = plugin.getSessionService();
        this.messages         = plugin.getMessages();
    }

    /**
     * Initiates the premium verification flow for {@code player}.
     * Checks login state, then delegates to {@link #verifyWithMojang} async.
     */
    public void setPremium(Player player) {
        if (!sessionService.isLoggedIn(player.getUniqueId())) {
            MessageUtil.send(player, messages.get("errors.not-logged-in"));
            return;
        }

        // FIX: explicit executor
        playerRepository.findByUuid(player.getUniqueId())
                .thenAcceptAsync(account -> {
                    if (account == null) {
                        plugin.getServer().getScheduler().runTask(plugin, () ->
                                MessageUtil.send(player, messages.get("errors.not-logged-in")));
                        return;
                    }
                    if (account.isPremiumLocked()) {
                        plugin.getServer().getScheduler().runTask(plugin, () ->
                                MessageUtil.send(player, messages.get("premium.already-verified")));
                        return;
                    }
                    // HTTP call is blocking — stays on our async thread
                    verifyWithMojang(player, account);
                }, plugin.getAsyncExecutor());
    }

    /**
     * Calls GET {@code https://api.mojang.com/users/profiles/minecraft/{username}}.
     *
     * <p>On success:
     * <ol>
     *   <li>Sets {@link PremiumState#PREMIUM_LOCKED} in the DB.</li>
     *   <li>Invalidates the current cracked session.</li>
     *   <li>Kicks the player with a reconnect message.</li>
     * </ol>
     * The player reconnects through Mojang auth and is auto-logged in.
     */
    private void verifyWithMojang(Player player, PlayerAccount account) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(MOJANG_API + player.getName()))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();

            if (status == 204 || status == 404) {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        MessageUtil.send(player, messages.get("premium.not-supported")));
                return;
            }
            if (status != 200) {
                plugin.getLogger().warning("Mojang API returned " + status + " for " + player.getName());
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        MessageUtil.send(player, messages.get("premium.not-supported")));
                return;
            }

            JSONObject json    = (JSONObject) new JSONParser().parse(response.body());
            String     mojangId = (String) json.get("id");
            if (mojangId == null || mojangId.isBlank()) {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        MessageUtil.send(player, messages.get("premium.not-supported")));
                return;
            }

            plugin.getLogger().info("[LoginX] Locking " + player.getName()
                    + " to Mojang UUID " + uuidFromHex(mojangId));

            account.setPremiumState(PremiumState.PREMIUM_LOCKED);
            playerRepository.save(account)
                    .thenRunAsync(() ->
                            sessionService.invalidate(player.getUniqueId()).thenRun(() ->
                                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                                        if (!player.isOnline()) return;
                                        player.kickPlayer(messages.getRaw("premium.verified-kick")
                                                .replace("{seconds}", String.valueOf(RECONNECT_DELAY_SECONDS)));
                                    })
                            ), plugin.getAsyncExecutor());   // FIX: explicit executor

        } catch (IOException | InterruptedException e) {
            plugin.getLogger().log(Level.WARNING, "Mojang API request failed for " + player.getName(), e);
            plugin.getServer().getScheduler().runTask(plugin, () ->
                    MessageUtil.send(player, messages.get("premium.not-supported")));
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
        } catch (ParseException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to parse Mojang API response", e);
            plugin.getServer().getScheduler().runTask(plugin, () ->
                    MessageUtil.send(player, messages.get("premium.not-supported")));
        }
    }

    /**
     * Admin shortcut: marks an account {@link PremiumState#PREMIUM_LOCKED} without
     * going through the Mojang API.
     *
     * @see me.abdoabk.loginX.command.LoginXAdminCommand
     */
    public void forcePremium(String targetName) {
        playerRepository.findByUsername(targetName)
                .thenAcceptAsync(account -> {
                    if (account == null) return;
                    account.setPremiumState(PremiumState.PREMIUM_LOCKED);
                    playerRepository.save(account);
                }, plugin.getAsyncExecutor());
    }

    /** Converts a 32-char hex UUID string (no hyphens) to a {@link UUID}. */
    private static UUID uuidFromHex(String hex) {
        return UUID.fromString(hex.replaceFirst(
                "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));
    }
}
