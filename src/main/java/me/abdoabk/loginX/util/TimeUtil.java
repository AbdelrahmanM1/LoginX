package me.abdoabk.loginX.util;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public final class TimeUtil {

    private TimeUtil() {}

    public static Instant nowPlusMinutes(int minutes) {
        return Instant.now().plus(minutes, ChronoUnit.MINUTES);
    }

    public static Instant nowPlusSeconds(int seconds) {
        return Instant.now().plus(seconds, ChronoUnit.SECONDS);
    }

    public static boolean isExpired(Instant expiry) {
        return Instant.now().isAfter(expiry);
    }

    public static Instant sevenDaysAgo() {
        return Instant.now().minus(7, ChronoUnit.DAYS);
    }
}