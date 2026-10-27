package app.vultrpocket.mobile;

final class RetentionPolicy {
    static final long LOGIN_TTL = 30L * 24 * 60 * 60 * 1000;
    static final long SNAPSHOT_TTL = 24L * 60 * 60 * 1000;
    static final long CLOCK_TOLERANCE = 5L * 60 * 1000;
    static boolean resumeNeedsReload(long backgroundMillis,boolean remembered,boolean systemMode,boolean ticketValid){
        return backgroundMillis>5*60*1000L&&(systemMode||!remembered||!ticketValid);
    }
    static boolean loginValid(long issued, long expires, long now, long lastSeen) {
        return issued > 0 && now > 0 && issued - now <= CLOCK_TOLERANCE
            && expires > Math.max(now,lastSeen) && expires > issued
            && expires - issued <= LOGIN_TTL && (lastSeen <= 0 || lastSeen - now <= CLOCK_TOLERANCE);
    }
    static boolean snapshotValid(long saved, long now, String month, String currentMonth) {
        return saved > 0 && now >= saved && now - saved <= SNAPSHOT_TTL
            && currentMonth != null && currentMonth.equals(month);
    }
}
