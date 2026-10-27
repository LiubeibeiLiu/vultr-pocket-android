package app.vultrpocket.mobile;

public final class RetentionPolicyTest {
    private static int passed;
    private static void check(boolean ok, String label){if(!ok)throw new AssertionError(label);passed++;}
    public static void main(String[] args){
        long issued=1000000,expires=issued+RetentionPolicy.LOGIN_TTL;
        check(RetentionPolicy.LOGIN_TTL==2592000000L,"30 day login limit");
        check(RetentionPolicy.loginValid(issued,expires,issued,issued),"first login");
        check(RetentionPolicy.loginValid(issued,expires,expires-1,issued),"valid until expiry");
        check(!RetentionPolicy.loginValid(issued,expires,expires,issued),"exact expiry");
        check(!RetentionPolicy.loginValid(issued,expires,expires+1,issued),"expired");
        check(!RetentionPolicy.loginValid(issued,expires,issued-RetentionPolicy.CLOCK_TOLERANCE-1,0),"future issue");
        check(!RetentionPolicy.loginValid(issued,expires+1,issued,issued),"longer than maximum");
        check(!RetentionPolicy.loginValid(issued,issued,issued,issued),"zero lifetime");
        check(!RetentionPolicy.loginValid(issued,issued-1,issued,issued),"negative lifetime");
        check(!RetentionPolicy.loginValid(0,expires,issued,0),"missing issue");
        check(!RetentionPolicy.loginValid(issued,expires,issued+20,issued+RetentionPolicy.CLOCK_TOLERANCE+21),"clock rollback");
        check(RetentionPolicy.loginValid(issued,expires,issued+20,issued+20),"same last seen");
        check(RetentionPolicy.SNAPSHOT_TTL==86400000L,"24 hour cache");
        check(RetentionPolicy.snapshotValid(issued,issued,"2026-10","2026-10"),"fresh cache");
        check(RetentionPolicy.snapshotValid(issued,issued+RetentionPolicy.SNAPSHOT_TTL,"2026-10","2026-10"),"cache last valid instant");
        check(!RetentionPolicy.snapshotValid(issued,issued+RetentionPolicy.SNAPSHOT_TTL+1,"2026-10","2026-10"),"cache expired");
        check(!RetentionPolicy.snapshotValid(issued,issued-1,"2026-10","2026-10"),"cache future timestamp");
        check(!RetentionPolicy.snapshotValid(issued,issued,"2026-09","2026-10"),"cache previous month");
        check(!RetentionPolicy.snapshotValid(issued,issued,"2026-10",null),"missing current month");
        check(!RetentionPolicy.snapshotValid(0,issued,"2026-10","2026-10"),"missing cache time");
        check(!RetentionPolicy.resumeNeedsReload(6*60*1000L,true,false,true),"remembered return keeps UI");
        check(!RetentionPolicy.resumeNeedsReload(8*60*60*1000L,true,false,true),"long background with valid ticket");
        check(RetentionPolicy.resumeNeedsReload(6*60*1000L,true,false,false),"expired remembered ticket reloads");
        check(RetentionPolicy.resumeNeedsReload(6*60*1000L,true,true,true),"system mode requires verification");
        check(RetentionPolicy.resumeNeedsReload(6*60*1000L,false,false,true),"non remembered mode reloads");
        check(!RetentionPolicy.resumeNeedsReload(5*60*1000L,false,true,false),"existing five minute boundary");
        check(RetentionPolicy.resumeNeedsReload(5*60*1000L+1,false,true,false),"past five minute boundary");
        check(!RetentionPolicy.resumeNeedsReload(0,false,true,false),"initial resume");
        check(RetentionPolicy.loginValid(issued,expires,issued-1000,issued),"small automatic clock correction");
        check(RetentionPolicy.loginValid(issued,expires,issued+1000,issued+301000),"clock tolerance boundary");
        check(!RetentionPolicy.loginValid(issued,expires,expires-1000,expires),"high water expires at original deadline");
        System.out.println("RetentionPolicy: "+passed+"/"+passed+" assertions passed");
    }
}
