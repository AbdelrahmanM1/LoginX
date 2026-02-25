package me.abdoabk.loginX.fingerprint;

import me.abdoabk.loginX.LoginX;
import me.abdoabk.loginX.config.ConfigService;
import me.abdoabk.loginX.util.TimeUtil;
import org.bukkit.entity.Player;

import java.sql.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FingerprintService {

    private final LoginX plugin;
    private final ConfigService config;
    private final Map<UUID, Fingerprint> cache = new ConcurrentHashMap<>();

    public FingerprintService(LoginX plugin, ConfigService config) {
        this.plugin = plugin;
        this.config = config;
    }

    public Fingerprint buildFingerprint(Player player) {
        String brand = getClientBrand(player);
        int protocol = getProtocol(player);
        String javaVersion = System.getProperty("java.version", "unknown");
        boolean proxy = false;

        Fingerprint fp = new Fingerprint(brand, protocol, javaVersion, proxy);
        cache.put(player.getUniqueId(), fp);
        return fp;
    }

    /**
     * Gets the client brand name safely.
     * Uses reflection to call Paper's getClientBrandName() if available,
     * otherwise falls back to "vanilla".
     */
    private String getClientBrand(Player player) {
        try {
            // Paper 1.19+ exposes this directly
            java.lang.reflect.Method method = player.getClass().getMethod("getClientBrandName");
            Object result = method.invoke(player);
            if (result instanceof String s && !s.isBlank()) return s;
        } catch (Exception ignored) {}
        return "vanilla";
    }

    /**
     * Gets the client protocol version safely.
     * Uses reflection to call Paper's getProtocolVersion() if available,
     * otherwise returns 0.
     */
    private int getProtocol(Player player) {
        try {
            java.lang.reflect.Method method = player.getClass().getMethod("getProtocolVersion");
            Object result = method.invoke(player);
            if (result instanceof Integer i) return i;
        } catch (Exception ignored) {}
        return 0;
    }

    public Fingerprint getCached(UUID uuid) {
        return cache.get(uuid);
    }

    public void remove(UUID uuid) {
        cache.remove(uuid);
    }

    /**
     * Records a fingerprint change in the DB for drift tracking.
     */
    public void recordChange(UUID uuid) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection con = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = con.prepareStatement(
                         "INSERT INTO fingerprint_changes (uuid, changed_at) VALUES (?, ?)")) {
                ps.setString(1, uuid.toString());
                ps.setLong(2, Instant.now().toEpochMilli());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to record fingerprint change: " + e.getMessage());
            }
        });
    }

    /**
     * Counts fingerprint changes in the last 7 days.
     */
    public int countChangesLast7Days(UUID uuid) {
        try (Connection con = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT COUNT(*) FROM fingerprint_changes WHERE uuid = ? AND changed_at > ?")) {
            ps.setString(1, uuid.toString());
            ps.setLong(2, TimeUtil.sevenDaysAgo().toEpochMilli());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to count fingerprint changes: " + e.getMessage());
        }
        return 0;
    }

    public boolean exceedsMaxChanges(UUID uuid) {
        return countChangesLast7Days(uuid) >= config.getMaxFingerprintChanges7d();
    }
}