package me.abdoabk.loginX.session;

import me.abdoabk.loginX.config.ConfigService;
import me.abdoabk.loginX.storage.SessionRepository;
import me.abdoabk.loginX.util.TimeUtil;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central login-state authority and session cache.
 *
 * <h2>In-memory state</h2>
 * <ul>
 *   <li>{@code loggedIn} — O(1) auth check; used by every event handler in
 *       {@link me.abdoabk.loginX.listener.PlayerRestrictListener}.</li>
 *   <li>{@code sessionCache} — avoids a DB hit on every reconnect when
 *       {@code performance.cache-sessions} is {@code true}.</li>
 * </ul>
 *
 * <h2>Fix: createSession() ordering</h2>
 * The old code added to {@code loggedIn} before the DB {@code save()} completed.
 * If the save failed the player would be in a ghost-logged-in state.
 * Now we only mark {@code loggedIn} inside the {@code thenRun()} callback —
 * after a successful persist.
 *
 * @see me.abdoabk.loginX.listener.PlayerRestrictListener
 * @see SessionRepository
 * @see ConfigService
 */
public class SessionService {

    private final SessionRepository repository;
    private final ConfigService config;

    /** Fast O(1) login check — updated only after successful DB operations. */
    private final Set<UUID> loggedIn = ConcurrentHashMap.newKeySet();

    /** Optional in-memory cache so we avoid a DB round-trip per reconnect. */
    private final Map<UUID, Session> sessionCache = new ConcurrentHashMap<>();

    public SessionService(SessionRepository repository, ConfigService config) {
        this.repository = repository;
        this.config     = config;
    }

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Loads a session from cache (if enabled) or the DB.
     *
     * @param uuid player UUID
     * @return future that resolves to the session, or {@code null} if none exists
     */
    public CompletableFuture<Session> getSession(UUID uuid) {
        if (config.isCacheSessions() && sessionCache.containsKey(uuid)) {
            return CompletableFuture.completedFuture(sessionCache.get(uuid));
        }
        return repository.findByUuid(uuid).thenApply(session -> {
            if (session != null && config.isCacheSessions()) {
                sessionCache.put(uuid, session);
            }
            return session;
        });
    }

    /**
     * Creates a new session and persists it to the DB.
     *
     * <p><b>FIX:</b> {@code loggedIn} is only updated inside the {@code thenRun()}
     * callback — i.e. after the DB save has successfully completed — to avoid a
     * ghost-logged-in state if the DB write fails.</p>
     *
     * @param uuid        player UUID
     * @param ip          player IP address
     * @param fingerprint SHA-256 fingerprint hash
     * @return future that completes when the session is persisted
     */
    public CompletableFuture<Void> createSession(UUID uuid, String ip, String fingerprint) {
        Instant expiry  = TimeUtil.nowPlusMinutes(config.getSessionTimeoutMinutes());
        Session session = new Session(uuid, ip, fingerprint, expiry);
        sessionCache.put(uuid, session);     // cache immediately (safe — worst case a stale entry)
        return repository.save(session).thenRun(() -> loggedIn.add(uuid));  // FIX: mark after save
    }

    /**
     * Removes a session from cache, marks the player logged-out, and deletes from DB.
     *
     * @param uuid player UUID
     * @return future that completes when deletion is done
     */
    public CompletableFuture<Void> invalidate(UUID uuid) {
        loggedIn.remove(uuid);
        sessionCache.remove(uuid);
        return repository.delete(uuid);
    }

    /**
     * Extends the session expiry for a rolling session.
     * No-op if the session is not in the cache.
     */
    public CompletableFuture<Void> updateExpiry(UUID uuid) {
        Session session = sessionCache.get(uuid);
        if (session != null) {
            session.setExpiresAt(TimeUtil.nowPlusMinutes(config.getSessionTimeoutMinutes()));
            return repository.save(session);
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * O(1) auth check — used by every event handler in
     * {@link me.abdoabk.loginX.listener.PlayerRestrictListener}.
     */
    public boolean isLoggedIn(UUID uuid) {
        return loggedIn.contains(uuid);
    }

    /**
     * Directly sets the logged-in state without touching the DB.
     * Used for bypass permission handling and session restore.
     */
    public void setLoggedIn(UUID uuid, boolean value) {
        if (value) loggedIn.add(uuid);
        else       loggedIn.remove(uuid);
    }

    /**
     * Purges expired sessions from both the DB and the in-memory cache.
     * Called by {@link me.abdoabk.loginX.session.SessionCleanupTask}.
     */
    public CompletableFuture<Void> cleanupExpired() {
        return repository.deleteExpired().thenRun(() ->
                sessionCache.entrySet().removeIf(e -> e.getValue().isExpired()));
    }
}
