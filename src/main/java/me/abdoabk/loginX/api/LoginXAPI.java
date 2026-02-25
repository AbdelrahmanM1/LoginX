package me.abdoabk.loginX.api;

import me.abdoabk.loginX.LoginX;
import me.abdoabk.loginX.model.PlayerAccount;
import me.abdoabk.loginX.session.SessionService;
import me.abdoabk.loginX.storage.PlayerRepository;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Public API surface for other plugins to interact with LoginX.
 *
 * <p>Initialized once via {@link #init(LoginX)} during {@link LoginX#onEnable()}.
 * After that, other plugins may call these static methods from any thread.</p>
 *
 * <h2>Internal wiring</h2>
 * <ul>
 *   <li>{@link #isLoggedIn(UUID)}       → {@link SessionService#isLoggedIn(UUID)}</li>
 *   <li>{@link #getAccount(UUID)}        → {@link PlayerRepository#findByUuid(UUID)}</li>
 *   <li>{@link #invalidateSession(UUID)} → {@link SessionService#invalidate(UUID)}</li>
 * </ul>
 *
 * @see LoginX#onEnable()  — calls LoginXAPI.init(this)
 * @see SessionService
 * @see PlayerRepository
 * @see PlayerAccount
 */
public class LoginXAPI {

    /** Set once by {@link LoginX#onEnable()}; never reassigned. */
    private static LoginX plugin;

    /**
     * Called by {@link LoginX#onEnable()} after all services are ready.
     * Must be called before any other method on this class.
     *
     * @param instance the running {@link LoginX} plugin instance
     */
    public static void init(LoginX instance) {
        plugin = instance;
    }

    /**
     * Checks whether a player is currently logged in (in-memory, O(1)).
     *
     * <p>Delegates to {@link SessionService#isLoggedIn(UUID)}.
     * Safe to call from any thread.</p>
     *
     * @param uuid the player's UUID
     * @return {@code true} if the player has an active session this runtime
     * @see SessionService#isLoggedIn(UUID)
     */
    public static boolean isLoggedIn(UUID uuid) {
        return plugin.getSessionService().isLoggedIn(uuid);
    }

    /**
     * Fetches a player's {@link PlayerAccount} from the database asynchronously.
     *
     * <p>Delegates to {@link PlayerRepository#findByUuid(UUID)}.
     * Returns {@code null} inside the future if no account exists.</p>
     *
     * @param uuid the player's UUID
     * @return a future that resolves to the account, or {@code null}
     * @see PlayerRepository#findByUuid(UUID)
     * @see PlayerAccount
     */
    public static CompletableFuture<PlayerAccount> getAccount(UUID uuid) {
        return plugin.getPlayerRepository().findByUuid(uuid);
    }

    /**
     * Invalidates a player's session — removes it from the in-memory cache
     * and deletes it from the {@code sessions} DB table.
     *
     * <p>Delegates to {@link SessionService#invalidate(UUID)}.
     * The player will be required to log in again on their next action.</p>
     *
     * @param uuid the player's UUID
     * @return a future that completes when the session is fully removed
     * @see SessionService#invalidate(UUID)
     */
    public static CompletableFuture<Void> invalidateSession(UUID uuid) {
        return plugin.getSessionService().invalidate(uuid);
    }
}
