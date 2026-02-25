package me.abdoabk.loginX.session;

import me.abdoabk.loginX.config.ConfigService;
import me.abdoabk.loginX.fingerprint.FingerprintPolicy;
import me.abdoabk.loginX.model.PlayerAccount;

/**
 * Validates a stored {@link Session} against the current player context.
 *
 * <p>Returns one of five {@link ValidationResult} values so the caller
 * ({@link me.abdoabk.loginX.auth.AuthService}) can handle each case explicitly.</p>
 *
 * @see FingerprintPolicy
 * @see me.abdoabk.loginX.auth.AuthService#handleJoin
 */
public class SessionValidator {

    private final ConfigService config;
    private final FingerprintPolicy fingerprintPolicy;

    public SessionValidator(ConfigService config, FingerprintPolicy fingerprintPolicy) {
        this.config            = config;
        this.fingerprintPolicy = fingerprintPolicy;
    }

    /** Possible outcomes of {@link #validate}. */
    public enum ValidationResult {
        /** Session is valid — auto-login the player. */
        VALID,
        /** Session has expired. */
        EXPIRED,
        /** Player's IP changed and {@code session.invalidate-on-ip-change} is true. */
        IP_MISMATCH,
        /** Player's fingerprint changed and policy requires re-login. */
        FINGERPRINT_MISMATCH,
        /** No session row exists for this player. */
        NO_SESSION
    }

    /**
     * Validates a session against the current player context.
     *
     * @param session            the stored session (may be {@code null})
     * @param account            the player's account
     * @param currentIp          player's current IP
     * @param currentFingerprint player's current device fingerprint hash
     * @return the validation outcome
     */
    public ValidationResult validate(Session session, PlayerAccount account,
                                     String currentIp, String currentFingerprint) {
        if (session == null)      return ValidationResult.NO_SESSION;
        if (session.isExpired())  return ValidationResult.EXPIRED;

        if (config.isInvalidateOnIpChange()
                && !session.getIp().equals(currentIp)) {
            return ValidationResult.IP_MISMATCH;
        }

        if (config.isFingerprintEnabled()
                && config.isInvalidateOnFingerprintChange()
                && session.getFingerprint() != null
                && !session.getFingerprint().equals(currentFingerprint)) {
            if (fingerprintPolicy.requiresReLogin(account, session.getFingerprint(), currentFingerprint)) {
                return ValidationResult.FINGERPRINT_MISMATCH;
            }
        }

        return ValidationResult.VALID;
    }
}
