package app.vultrpocket.mobile;

final class RefreshPolicy {
    static final long MIN_INTERVAL_MS=60000;
    static boolean validMinutes(int minutes){return minutes==0||minutes==1||minutes==3||minutes==5;}
    static boolean succeeded(boolean known,boolean cached,boolean stale,boolean paused,int issues){return known&&!cached&&!stale&&!paused&&issues==0;}
}
