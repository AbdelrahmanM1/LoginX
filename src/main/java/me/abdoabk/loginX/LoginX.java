package me.abdoabk.loginX;

import me.abdoabk.loginX.api.LoginXAPI;
import me.abdoabk.loginX.auth.*;
import me.abdoabk.loginX.command.*;
import me.abdoabk.loginX.config.ConfigService;
import me.abdoabk.loginX.config.Messages;
import me.abdoabk.loginX.fingerprint.FingerprintPolicy;
import me.abdoabk.loginX.fingerprint.FingerprintService;
import me.abdoabk.loginX.listener.PlayerJoinListener;
import me.abdoabk.loginX.listener.PlayerQuitListener;
import me.abdoabk.loginX.listener.PlayerRestrictListener;
import me.abdoabk.loginX.premium.PremiumService;
import me.abdoabk.loginX.security.AltLimitService;
import me.abdoabk.loginX.security.AntiReplayService;
import me.abdoabk.loginX.security.BruteForceService;
import me.abdoabk.loginX.session.SessionCleanupTask;
import me.abdoabk.loginX.session.SessionService;
import me.abdoabk.loginX.session.SessionValidator;
import me.abdoabk.loginX.storage.DatabaseManager;
import me.abdoabk.loginX.storage.PlayerRepository;
import me.abdoabk.loginX.storage.SessionRepository;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * LoginX — main plugin entry point and service locator.
 *
 * <p>Every service, repository, and listener is constructed here in
 * dependency order.  All other classes receive a {@code LoginX plugin}
 * reference and call {@code plugin.getXxx()} to resolve their deps.</p>
 *
 * <h2>Boot order inside {@link #onEnable()}</h2>
 * <pre>
 * ConfigService / Messages
 *   └─ DatabaseManager
 *        ├─ PlayerRepository  ──► AuthService, RegisterService, LoginService,
 *        │                         ChangePasswordService, PremiumService,
 *        │                         AltLimitService, PlayerRestrictListener,
 *        │                         LoginXAdminCommand
 *        └─ SessionRepository ──► SessionService
 *             └─ SessionService ──► AuthService, LoginService, RegisterService,
 *                                   ChangePasswordService, PremiumService,
 *                                   LoginTimeoutService, LogoutCommand,
 *                                   LoginXAdminCommand, PlayerRestrictListener,
 *                                   LoginXAPI
 * FingerprintService ──► FingerprintPolicy ──► SessionValidator
 *                                                └─ AuthService
 * BruteForceService  ──► LoginService, LoginXAdminCommand
 * AltLimitService    ──► RegisterService
 * PasswordPolicy     ──► RegisterService, ChangePasswordService
 * LoginTimeoutService ─► AuthService, LoginService, RegisterService, LogoutCommand
 * </pre>
 *
 * @author abdoabk
 * @see ConfigService
 * @see DatabaseManager
 * @see PlayerRepository
 * @see SessionRepository
 * @see SessionService
 * @see FingerprintService
 * @see AuthService
 * @see LoginXAPI
 */
public final class LoginX extends JavaPlugin {

    // ── Core ─────────────────────────────────────────────────────────────────

    /** Read by almost every service. @see ConfigService */
    private ConfigService configService;

    /** Used by every service that sends chat messages to players. @see Messages */
    private Messages messages;

    /**
     * Stored as a field so other services can call
     * {@link PlayerRestrictListener#applyRestrictions(org.bukkit.entity.Player)} and
     * {@link PlayerRestrictListener#removeRestrictions(org.bukkit.entity.Player)}.
     * Callers: {@link LoginService}, {@link RegisterService}, {@link LogoutCommand},
     * {@link PlayerJoinListener}.
     */
    private PlayerRestrictListener restrictListener;

    /**
     * Manages per-player kick-timers for unauthenticated players.
     * Started by: {@link AuthService#handleJoin}, {@link LogoutCommand}.
     * Cancelled by: {@link LoginService#login}, {@link RegisterService#register}.
     * @see LoginTimeoutService
     */
    private LoginTimeoutService loginTimeoutService;

    /**
     * HikariCP connection pool wrapper.
     * Used directly by: {@link FingerprintService}, {@link BruteForceService}
     * (raw SQL); indirectly by every repository.
     * @see DatabaseManager
     */
    private DatabaseManager databaseManager;

    /**
     * Shared thread pool (4 threads) for all async DB operations.
     * Passed into {@link PlayerRepository} and {@link SessionRepository};
     * also used by {@link BruteForceService#isBannedAsync(String)}.
     */
    private final Executor asyncExecutor = Executors.newFixedThreadPool(4);

    // ── Repositories ─────────────────────────────────────────────────────────

    /**
     * CRUD for the {@code players} table.
     * Used by: {@link AuthService}, {@link LoginService}, {@link RegisterService},
     * {@link ChangePasswordService}, {@link PremiumService}, {@link AltLimitService},
     * {@link PlayerRestrictListener}, {@link LoginXAdminCommand}.
     * @see PlayerRepository
     */
    private PlayerRepository playerRepository;

    /**
     * CRUD for the {@code sessions} table.
     * Used exclusively through {@link SessionService} (which owns the cache layer).
     * @see SessionRepository
     */
    private SessionRepository sessionRepository;

    // ── Services ─────────────────────────────────────────────────────────────

    /**
     * Builds {@link me.abdoabk.loginX.fingerprint.Fingerprint} objects from
     * a player's client brand + protocol version.  Also records drift events
     * to the {@code fingerprint_changes} DB table.
     * Used by: {@link AuthService}, {@link LoginService}, {@link RegisterService},
     * {@link PlayerJoinListener}, {@link FingerprintPolicy}.
     * @see FingerprintService
     */
    private FingerprintService fingerprintService;

    /**
     * Business rules for fingerprint mismatches — decides whether a mismatch
     * forces re-login (strict for premium, drift-limited for cracked).
     * Used by: {@link SessionValidator}.
     * @see FingerprintPolicy
     */
    private FingerprintPolicy fingerprintPolicy;

    /**
     * Central login-state authority.  Maintains an in-memory {@code loggedIn}
     * set and a session cache.  {@link SessionService#isLoggedIn(java.util.UUID)}
     * is called by every event handler in {@link PlayerRestrictListener}.
     * @see SessionService
     */
    private SessionService sessionService;

    /**
     * Validates a stored {@link me.abdoabk.loginX.session.Session} against
     * the current player context (IP, fingerprint, expiry).
     * Used exclusively by: {@link AuthService#handleJoin}.
     * @see SessionValidator
     */
    private SessionValidator sessionValidator;

    /**
     * Tracks failed login attempts per IP and per player UUID.
     * Bans IPs that exceed the configured threshold.
     * Used by: {@link LoginService}, {@link LoginXAdminCommand}.
     * @see BruteForceService
     */
    private BruteForceService bruteForceService;

    /**
     * Checks how many accounts are registered for a given IP.
     * Used exclusively by: {@link RegisterService}.
     * @see AltLimitService
     */
    private AltLimitService altLimitService;

    /**
     * Replay-attack guard — tracks used session tokens in memory.
     * Currently instantiated for future token-based session support.
     * @see AntiReplayService
     */
    private AntiReplayService antiReplayService;

    /**
     * Enforces minimum password length from config.
     * Used by: {@link RegisterService}, {@link ChangePasswordService}.
     * @see PasswordPolicy
     */
    private PasswordPolicy passwordPolicy;

    /**
     * Orchestrates the join/quit auth flow.
     * Called by: {@link PlayerJoinListener}, {@link PlayerQuitListener}.
     * @see AuthService
     */
    private AuthService authService;

    /**
     * Handles the /register command flow end-to-end.
     * Called by: {@link RegisterCommand}.
     * @see RegisterService
     */
    private RegisterService registerService;

    /**
     * Handles the /login command flow end-to-end.
     * Called by: {@link LoginCommand}.
     * @see LoginService
     */
    private LoginService loginService;

    /**
     * Handles the /changepass command flow end-to-end.
     * Called by: {@link ChangePassCommand}.
     * @see ChangePasswordService
     */
    private ChangePasswordService changePasswordService;

    /**
     * Verifies Mojang premium status and locks accounts.
     * Called by: {@link PremiumCommand}, {@link LoginXAdminCommand}.
     * @see PremiumService
     */
    private PremiumService premiumService;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void onEnable() {
        // 1. Config — must be first; every service reads config values
        saveDefaultConfig();
        configService = new ConfigService(this);
        messages      = new Messages(this);

        // 2. Database — DatabaseManager reads type/host/credentials from ConfigService
        databaseManager = new DatabaseManager(this, configService);
        try {
            databaseManager.init();
        } catch (SQLException e) {
            getLogger().severe("Failed to initialize database: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 3. Repositories — both share asyncExecutor for consistent thread usage
        playerRepository  = new PlayerRepository(databaseManager, asyncExecutor);
        sessionRepository = new SessionRepository(databaseManager, asyncExecutor);

        // 4. Services — order matters: FingerprintService before FingerprintPolicy,
        //               SessionService before LoginTimeoutService
        fingerprintService  = new FingerprintService(this, configService);
        fingerprintPolicy   = new FingerprintPolicy(configService, fingerprintService);
        sessionService      = new SessionService(sessionRepository, configService);
        sessionValidator    = new SessionValidator(configService, fingerprintPolicy);
        bruteForceService   = new BruteForceService(this, configService);
        loginTimeoutService = new LoginTimeoutService(this); // resolves sessionService via getter
        altLimitService     = new AltLimitService(playerRepository, configService);
        antiReplayService   = new AntiReplayService();
        passwordPolicy      = new PasswordPolicy(configService);

        // 5. Auth services — all resolve deps via plugin.getXxx()
        authService           = new AuthService(this);
        registerService       = new RegisterService(this);
        loginService          = new LoginService(this);
        changePasswordService = new ChangePasswordService(this);
        premiumService        = new PremiumService(this);

        // 6. Public API — other plugins may call LoginXAPI.isLoggedIn() etc. after this
        LoginXAPI.init(this);

        // 7. Commands and Listeners
        registerCommands();
        registerListeners();

        // 8. Cleanup task — runs every cleanup-task-minutes (config) on async thread
        long cleanupInterval = configService.getCleanupTaskMinutes() * 60 * 20L;
        new SessionCleanupTask(this, sessionService)
                .runTaskTimerAsynchronously(this, cleanupInterval, cleanupInterval);

        getLogger().info("LoginX enabled successfully.");
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.close(); // closes HikariCP pool gracefully
        }
        getLogger().info("LoginX disabled.");
    }

    /**
     * Wires all command executors.
     * @see RegisterCommand  — /register
     * @see LoginCommand     — /login
     * @see LogoutCommand    — /logout
     * @see ChangePassCommand — /changepass
     * @see PremiumCommand   — /premium
     * @see LoginXAdminCommand — /loginx
     */
    private void registerCommands() {
        getCommand("register").setExecutor(new RegisterCommand(this));
        getCommand("login").setExecutor(new LoginCommand(this));
        getCommand("logout").setExecutor(new LogoutCommand(this));
        getCommand("changepass").setExecutor(new ChangePassCommand(this));
        getCommand("premium").setExecutor(new PremiumCommand(this));
        getCommand("loginx").setExecutor(new LoginXAdminCommand(this));
    }

    /**
     * Registers Bukkit event listeners.
     * {@link PlayerRestrictListener} is stored in a field so other services
     * can call {@code plugin.getRestrictListener().removeRestrictions(player)}.
     *
     * @see PlayerJoinListener  — entry point for auth flow on join
     * @see PlayerQuitListener  — cleanup on quit
     * @see PlayerRestrictListener — blocks all actions for unauthenticated players
     */
    private void registerListeners() {
        var pm = getServer().getPluginManager();
        pm.registerEvents(new PlayerJoinListener(this), this);
        pm.registerEvents(new PlayerQuitListener(this), this);
        restrictListener = new PlayerRestrictListener(this);
        pm.registerEvents(restrictListener, this);
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    /** @return config wrapper — used by almost every service */
    public ConfigService getConfigService()             { return configService; }

    /** @return messages wrapper — used by every service that sends player messages */
    public Messages getMessages()                       { return messages; }

    /**
     * @return the restriction listener — allows {@link LoginService},
     *         {@link RegisterService}, {@link LogoutCommand}, and
     *         {@link PlayerJoinListener} to call removeRestrictions/applyRestrictions
     */
    public PlayerRestrictListener getRestrictListener() { return restrictListener; }

    /** @return HikariCP-backed DB manager */
    public DatabaseManager getDatabaseManager()         { return databaseManager; }

    /**
     * @return timeout service — started by {@link AuthService#handleJoin} and
     *         {@link LogoutCommand}; cancelled by {@link LoginService#login} and
     *         {@link RegisterService#register}
     */
    public LoginTimeoutService getLoginTimeoutService() { return loginTimeoutService; }

    /** @return {@code players} table repository */
    public PlayerRepository getPlayerRepository()       { return playerRepository; }

    /** @return {@code sessions} table repository */
    public SessionRepository getSessionRepository()     { return sessionRepository; }

    /**
     * @return fingerprint service — called by {@link AuthService}, {@link LoginService},
     *         {@link RegisterService}, and {@link PlayerJoinListener} to build/store fingerprints
     */
    public FingerprintService getFingerprintService()   { return fingerprintService; }

    /** @return fingerprint mismatch policy — used by {@link SessionValidator} */
    public FingerprintPolicy getFingerprintPolicy()     { return fingerprintPolicy; }

    /**
     * @return the session manager — {@link SessionService#isLoggedIn(java.util.UUID)}
     *         is called by every event handler in {@link PlayerRestrictListener}
     */
    public SessionService getSessionService()           { return sessionService; }

    /** @return session validator — used exclusively by {@link AuthService#handleJoin} */
    public SessionValidator getSessionValidator()       { return sessionValidator; }

    /**
     * @return brute-force service — used by {@link LoginService} for ban checks
     *         and by {@link LoginXAdminCommand} for unban
     */
    public BruteForceService getBruteForceService()     { return bruteForceService; }

    /** @return alt-account IP limiter — used exclusively by {@link RegisterService} */
    public AltLimitService getAltLimitService()         { return altLimitService; }

    /** @return replay-attack guard (future token auth) */
    public AntiReplayService getAntiReplayService()     { return antiReplayService; }

    /** @return password policy — used by {@link RegisterService} and {@link ChangePasswordService} */
    public PasswordPolicy getPasswordPolicy()           { return passwordPolicy; }

    /** @return join/quit auth orchestrator — called by {@link PlayerJoinListener} and {@link PlayerQuitListener} */
    public AuthService getAuthService()                 { return authService; }

    /** @return registration flow handler — called by {@link RegisterCommand} */
    public RegisterService getRegisterService()         { return registerService; }

    /** @return login flow handler — called by {@link LoginCommand} */
    public LoginService getLoginService()               { return loginService; }

    /** @return change-password flow handler — called by {@link ChangePassCommand} */
    public ChangePasswordService getChangePasswordService() { return changePasswordService; }

    /** @return Mojang-verification premium service — called by {@link PremiumCommand} and {@link LoginXAdminCommand} */
    public PremiumService getPremiumService()           { return premiumService; }

    /**
     * @return shared 4-thread async executor used by all CompletableFuture chains.
     * @see PlayerRepository
     * @see SessionRepository
     * @see BruteForceService#isBannedAsync(String)
     * @see BruteForceService#unbanIp(String)
     */
    public Executor getAsyncExecutor()                  { return asyncExecutor; }
}
