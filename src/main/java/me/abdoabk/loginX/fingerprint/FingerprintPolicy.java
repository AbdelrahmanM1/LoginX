package me.abdoabk.loginX.fingerprint;

import me.abdoabk.loginX.config.ConfigService;
import me.abdoabk.loginX.model.PlayerAccount;

public class FingerprintPolicy {

    private final ConfigService config;
    private final FingerprintService fingerprintService;

    public FingerprintPolicy(ConfigService config, FingerprintService fingerprintService) {
        this.config = config;
        this.fingerprintService = fingerprintService;
    }

    /**
     * Determine if a fingerprint mismatch should require re-login.
     * @param account  the player account
     * @param storedFp fingerprint hash stored in session
     * @param currentFp current fingerprint hash
     * @return true if re-login is required
     */
    public boolean requiresReLogin(PlayerAccount account, String storedFp, String currentFp) {
        if (!config.isFingerprintEnabled()) return false;
        if (storedFp == null || currentFp == null) return false;
        if (storedFp.equals(currentFp)) return false;

        // If strict premium mode is on and account is premium, always require re-login
        if (config.isStrictFingerprintForPremium() && account.isPremiumLocked()) {
            return true;
        }

        // Otherwise check if within drift limit
        return fingerprintService.exceedsMaxChanges(account.getUuid());
    }
}