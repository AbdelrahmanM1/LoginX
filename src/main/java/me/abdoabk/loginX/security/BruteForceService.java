package me.abdoabk.loginX.security;

import me.abdoabk.loginX.LoginX;
import me.abdoabk.loginX.config.ConfigService;

import java.sql.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks failed login attempts per IP and per player UUID.
 * Bans IPs that exceed the configured threshold.
 *
 * <h2>Fix: consistent async execution</h2>
 * The old {@link #banIp} used {@code runTaskAsynchronously} (Bukkit scheduler),
 * mixing two async systems. Now uses {@code plugin.getAsyncExecutor()} exclusively.
 *
 * @see ConfigService#getBruteForceMaxAttempts()
 * @see ConfigService#getBruteForceTempBanMinutes()
 * @see me.abdoabk.loginX.auth.LoginService
 * @see me.abdoabk.loginX.command.LoginXAdminCommand
 */
public class BruteForceService {

    private final LoginX plugin;
    private final ConfigService config;

    /** IP → cumulative failed attempt count this runtime */
    private final Map<String, Integer> attempts       = new ConcurrentHashMap<>();
    /** UUID → failed attempt count (for per-player kick threshold) */
    private final Map<UUID,   Integer> playerAttempts = new ConcurrentHashMap<>();

    public BruteForceService(LoginX plugin, ConfigService config) {
        this.plugin  = plugin;
        this.config  = config;
    }

    /**
     * Async-safe ban check.
     * Wraps the synchronous {@link #isBanned(String)} in the plugin's async executor.
     *
     * @param ip the IP to check
     * @return future resolving to {@code true} if the IP is currently banned
     */
    public CompletableFuture<Boolean> isBannedAsync(String ip) {
        return CompletableFuture.supplyAsync(() -> isBanned(ip), plugin.getAsyncExecutor());
    }

    /**
     * Synchronous ban check — must be called from an async context.
     * Reads {@code banned_until} from the {@code brute_force} table.
     *
     * @param ip the IP to check
     * @return {@code true} if the IP has an active ban
     */
    public boolean isBanned(String ip) {
        try (Connection con = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT banned_until FROM brute_force WHERE ip = ?")) {
            ps.setString(1, ip);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                long bannedUntil = rs.getLong("banned_until");
                return bannedUntil > Instant.now().toEpochMilli();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("BruteForce isBanned error: " + e.getMessage());
        }
        return false;
    }

    /**
     * Returns the remaining ban duration in seconds, or {@code 0} if not banned.
     *
     * @param ip the IP to query
     * @return seconds remaining, or 0
     */
    public long getBanRemainingSeconds(String ip) {
        try (Connection con = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT banned_until FROM brute_force WHERE ip = ?")) {
            ps.setString(1, ip);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                long remaining = rs.getLong("banned_until") - Instant.now().toEpochMilli();
                return remaining > 0 ? remaining / 1000 : 0;
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("BruteForce getBanRemaining error: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Records a failed login attempt. If the IP reaches the configured max, bans it.
     *
     * @param ip         the player's IP address
     * @param playerUuid the player's UUID
     * @return the new per-player attempt count
     */
    public int recordFailure(String ip, UUID playerUuid) {
        int ipCount     = attempts.merge(ip, 1, Integer::sum);
        int playerCount = playerAttempts.merge(playerUuid, 1, Integer::sum);

        if (ipCount >= config.getBruteForceMaxAttempts()) {
            banIp(ip);
            attempts.remove(ip);
        }

        return playerCount;
    }

    /**
     * Clears all attempt counters for an IP and player UUID after a successful login.
     *
     * @param ip         the player's IP
     * @param playerUuid the player's UUID
     */
    public void clearAttempts(String ip, UUID playerUuid) {
        attempts.remove(ip);
        playerAttempts.remove(playerUuid);
    }

    /**
     * Admin command: immediately lift the ban for an IP.
     * Deletes the row from the {@code brute_force} table and clears in-memory counters.
     *
     * @param ip the IP to unban
     * @return future that completes when the unban is done
     * @see me.abdoabk.loginX.command.LoginXAdminCommand
     */
    public CompletableFuture<Void> unbanIp(String ip) {
        return CompletableFuture.runAsync(() -> {
            try (Connection con = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = con.prepareStatement(
                         "DELETE FROM brute_force WHERE ip = ?")) {
                ps.setString(1, ip);
                ps.executeUpdate();
                attempts.remove(ip);
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to unban IP: " + e.getMessage());
            }
        }, plugin.getAsyncExecutor());   // FIX: use our executor, not Bukkit scheduler
    }

    // ─────────────────────────────────────────────────────────────────────────

    private void banIp(String ip) {
        long bannedUntil = Instant.now().toEpochMilli()
                + (config.getBruteForceTempBanMinutes() * 60_000L);
        // FIX: use plugin.getAsyncExecutor() consistently instead of runTaskAsynchronously
        plugin.getAsyncExecutor().execute(() -> {
            try (Connection con = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = con.prepareStatement("""
                         INSERT INTO brute_force (ip, attempts, banned_until)
                         VALUES (?, ?, ?)
                         ON CONFLICT(ip) DO UPDATE SET
                           attempts     = excluded.attempts,
                           banned_until = excluded.banned_until
                         """)) {
                ps.setString(1, ip);
                ps.setInt(2, config.getBruteForceMaxAttempts());
                ps.setLong(3, bannedUntil);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to ban IP: " + e.getMessage());
            }
        });
    }
}
