package app.vultrpocket.mobile;

public final class ApiErrorPolicyTest {
    public static void main(String[] args) {
        check(ApiErrorPolicy.classify(401,"Unauthorized IP address: 192.0.2.15").equals("source_ip_denied"));
        check(ApiErrorPolicy.classify(403,"IP address is not allowed").equals("source_ip_denied"));
        check(ApiErrorPolicy.classify(401,"Invalid API key").equals("upstream_401"));
        check(ApiErrorPolicy.classify(403,"Insufficient permission").equals("upstream_403"));
        check(ApiErrorPolicy.classify(429,"").equals("upstream_429"));
        check(ApiErrorPolicy.sourceIp("Unauthorized IP address: 192.0.2.15").equals("192.0.2.15"));
        check(ApiErrorPolicy.sourceIp("Unauthorized IP address: 2001:db8::5").equals("2001:db8::5"));
        check(ApiErrorPolicy.sourceIp("Invalid API key: private-credential").isEmpty());
        check(ApiErrorPolicy.message("source_ip_denied").contains("来源 IP"));
        check(!ApiErrorPolicy.message("unknown private response").contains("private"));
        System.out.println("ApiErrorPolicy: 10/10 assertions passed");
    }
    private static void check(boolean value){if(!value)throw new AssertionError();}
}
