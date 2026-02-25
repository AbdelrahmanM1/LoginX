package me.abdoabk.loginX.config;

import me.abdoabk.loginX.LoginX;
import me.abdoabk.loginX.auth.LoginTimeoutService;
import me.abdoabk.loginX.auth.PasswordPolicy;
import me.abdoabk.loginX.fingerprint.FingerprintPolicy;
import me.abdoabk.loginX.fingerprint.FingerprintService;
import me.abdoabk.loginX.security.AltLimitService;
import me.abdoabk.loginX.security.BruteForceService;
import me.abdoabk.loginX.session.SessionService;
import me.abdoabk.loginX.session.SessionValidator;
import me.abdoabk.loginX.storage.DatabaseManager;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Thin, typed wrapper around Bukkit's {@link FileConfiguration} for {@code config.yml}.
 *
 * <p>Provides a single source of truth for every config key in the plugin.
 * All getters have safe defaults so the plugin operates out-of-the-box even
 * with an empty config file.</p>
 *
 * <h2>Consumers</h2>
 * <ul>
 *   <li>{@link DatabaseManager}       — database.type / mysql.*</li>
 *   <li>{@link SessionService}        — session.timeout-minutes, session.rolling, cache-sessions</li>
 *   <li>{@link SessionValidator}      — session.invalidate-on-ip-change / fingerprint-change</li>
 *   <li>{@link FingerprintService}    — fingerprint.max-changes-per-7d</li>
 *   <li>{@link FingerprintPolicy}     — fingerprint.enabled, fingerprint.strict-for-premium</li>
 *   <li>{@link BruteForceService}     — security.brute-force.*</li>
 *   <li>{@link AltLimitService}       — security.max-accounts-per-ip</li>
 *   <li>{@link PasswordPolicy}        — auth.min-password-length</li>
 *   <li>{@link LoginTimeoutService}   — auth.login-timeout-seconds</li>
 *   <li>{@link LoginX}                — performance.cleanup-task-minutes</li>
 * </ul>
 *
 * <h2>Reload</h2>
 * {@link #reload()} is called by {@code /loginx reload} via
 * {@link me.abdoabk.loginX.command.LoginXAdminCommand}.
 *
 * @see LoginX#getConfigService()
 */
public class ConfigService {

    private final LoginX plugin;
    private FileConfiguration config;

    /**
     * Constructs the service and immediately calls {@link #reload()}
     * so {@code config} is never {@code null}.
     *
     * @param plugin the main plugin instance
     */
    public ConfigService(LoginX plugin) {
        this.plugin = plugin;
        reload();
    }

    /**
     * Re-reads {@code config.yml} from disk.
     * Called on construction and by {@link me.abdoabk.loginX.command.LoginXAdminCommand} on reload.
     */
    public void reload() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();
    }

    // ── Auth ──────────────────────────────────────────────────────────────────

    /** @return auth.allow-cracked — whether offline-mode players may register */
    public boolean isAllowCracked()       { return config.getBoolean("auth.allow-cracked", true); }

    /**
     * @return auth.min-password-length
     * @see PasswordPolicy#meetsMinLength(String)
     */
    public int getMinPasswordLength()     { return config.getInt("auth.min-password-length", 8); }

    /**
     * @return auth.login-timeout-seconds
     * @see LoginTimeoutService#startTimeout(org.bukkit.entity.Player)
     */
    public int getLoginTimeoutSeconds()   { return config.getInt("auth.login-timeout-seconds", 30); }

    // ── Session ───────────────────────────────────────────────────────────────

    /** @return session.enabled */
    public boolean isSessionEnabled()     { return config.getBoolean("session.enabled", true); }

    /**
     * @return session.timeout-minutes
     * @see SessionService#createSession(java.util.UUID, String, String)
     * @see SessionService#updateExpiry(java.util.UUID)
     */
    public int getSessionTimeoutMinutes() { return config.getInt("session.timeout-minutes", 30); }

    /**
     * @return session.rolling — if true, expiry is extended on each reconnect
     * @see me.abdoabk.loginX.auth.AuthService#handleJoin(org.bukkit.entity.Player)
     */
    public boolean isRollingSession()     { return config.getBoolean("session.rolling", true); }

    /**
     * @return session.invalidate-on-ip-change
     * @see SessionValidator#validate
     */
    public boolean isInvalidateOnIpChange()           { return config.getBoolean("session.invalidate-on-ip-change", true); }

    /**
     * @return session.invalidate-on-fingerprint-change
     * @see SessionValidator#validate
     */
    public boolean isInvalidateOnFingerprintChange()  { return config.getBoolean("session.invalidate-on-fingerprint-change", true); }

    // ── Fingerprint ───────────────────────────────────────────────────────────

    /**
     * @return fingerprint.enabled
     * @see SessionValidator#validate
     * @see FingerprintPolicy#requiresReLogin
     */
    public boolean isFingerprintEnabled()             { return config.getBoolean("fingerprint.enabled", true); }

    /**
     * @return fingerprint.strict-for-premium — always re-login on mismatch for premium accounts
     * @see FingerprintPolicy#requiresReLogin
     */
    public boolean isStrictFingerprintForPremium()    { return config.getBoolean("fingerprint.strict-for-premium", true); }

    /**
     * @return fingerprint.max-changes-per-7d
     * @see FingerprintService#exceedsMaxChanges(java.util.UUID)
     */
    public int getMaxFingerprintChanges7d()           { return config.getInt("fingerprint.max-changes-per-7d", 1); }

    // ── Premium ───────────────────────────────────────────────────────────────

    /** @return premium.enabled */
    public boolean isPremiumEnabled()                 { return config.getBoolean("premium.enabled", true); }

    /** @return premium.auto-login-premium */
    public boolean isAutoLoginPremium()               { return config.getBoolean("premium.auto-login-premium", true); }

    /** @return premium.kick-cracked-on-premium-name */
    public boolean isKickCrackedOnPremiumName()       { return config.getBoolean("premium.kick-cracked-on-premium-name", true); }

    // ── Security ──────────────────────────────────────────────────────────────

    /**
     * @return security.max-accounts-per-ip
     * @see AltLimitService#isOverLimit(String)
     */
    public int getMaxAccountsPerIp()                  { return config.getInt("security.max-accounts-per-ip", 3); }

    /**
     * @return security.brute-force.max-attempts
     * @see BruteForceService#recordFailure(String, java.util.UUID)
     */
    public int getBruteForceMaxAttempts()             { return config.getInt("security.brute-force.max-attempts", 5); }

    /**
     * @return security.brute-force.temp-ban-minutes
     * @see BruteForceService — used when computing bannedUntil timestamp
     */
    public int getBruteForceTempBanMinutes()          { return config.getInt("security.brute-force.temp-ban-minutes", 10); }

    /** @return security.anti-replay.enabled */
    public boolean isAntiReplayEnabled()              { return config.getBoolean("security.anti-replay.enabled", true); }

    // ── Database ──────────────────────────────────────────────────────────────

    /**
     * @return database.type — {@code "sqlite"} or {@code "mysql"}
     * @see DatabaseManager#init()
     */
    public String getDatabaseType()   { return config.getString("database.type", "sqlite"); }

    /** @return database.mysql.host */
    public String getMysqlHost()      { return config.getString("database.mysql.host", "localhost"); }

    /** @return database.mysql.port */
    public int getMysqlPort()         { return config.getInt("database.mysql.port", 3306); }

    /** @return database.mysql.database */
    public String getMysqlDatabase()  { return config.getString("database.mysql.database", "loginx"); }

    /** @return database.mysql.username */
    public String getMysqlUsername()  { return config.getString("database.mysql.username", "root"); }

    /** @return database.mysql.password */
    public String getMysqlPassword()  { return config.getString("database.mysql.password", ""); }

    // ── Performance ───────────────────────────────────────────────────────────

    /** @return performance.async-database */
    public boolean isAsyncDatabase()  { return config.getBoolean("performance.async-database", true); }

    /**
     * @return performance.cache-sessions — enables the in-memory session cache
     * @see SessionService#getSession(java.util.UUID)
     */
    public boolean isCacheSessions()  { return config.getBoolean("performance.cache-sessions", true); }

    /**
     * @return performance.cleanup-task-minutes
     * @see me.abdoabk.loginX.session.SessionCleanupTask
     * @see LoginX#onEnable()
     */
    public int getCleanupTaskMinutes() { return config.getInt("performance.cleanup-task-minutes", 10); }
}
