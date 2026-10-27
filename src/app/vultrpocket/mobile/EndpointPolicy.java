package app.vultrpocket.mobile;

import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

final class EndpointPolicy {
    private static final Set<String> API = new HashSet<>(Arrays.asList("/instances", "/account", "/account/bandwidth", "/billing/invoices", "/regions", "/plans"));
    static boolean apiPath(String path) { return path != null && (API.contains(path) || path.matches("/instances/[a-zA-Z0-9-]{1,64}(/bandwidth)?")); }
    static boolean assetOrigin(String value) {
        try { URI u=new URI(value);return "https".equalsIgnoreCase(u.getScheme())&&"appassets.androidplatform.net".equalsIgnoreCase(u.getHost())&&u.getRawUserInfo()==null&&(u.getPort()==-1||u.getPort()==443); }
        catch(Exception e){return false;}
    }
}
