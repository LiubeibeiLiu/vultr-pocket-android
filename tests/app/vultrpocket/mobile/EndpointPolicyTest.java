package app.vultrpocket.mobile;

public final class EndpointPolicyTest {
    public static void main(String[] args) {
        int passed=0;
        String[] allowed={"/instances","/account","/account/bandwidth","/billing/invoices","/regions","/plans","/instances/abc-123/bandwidth","/instances/abc-123"};
        for(String p:allowed){if(!EndpointPolicy.apiPath(p))throw new AssertionError(p);passed++;}
        String[] denied={"/instances/x/halt","/instances/x/reboot","/account/../users","https://evil.example/account","/instances/x%2f..%2f/bandwidth","/instances/x/bandwidth?url=evil","/instances/../bandwidth","/instances/x/bandwidth/","/users",null};
        for(String p:denied){if(EndpointPolicy.apiPath(p))throw new AssertionError(p);passed++;}
        String[] origins={"https://appassets.androidplatform.net/assets/index.html","https://APPASSETS.ANDROIDPLATFORM.NET:443/app.js"};
        for(String p:origins){if(!EndpointPolicy.assetOrigin(p))throw new AssertionError(p);passed++;}
        String[] attacks={"http://appassets.androidplatform.net/","https://appassets.androidplatform.net.evil.example/","https://appassets.androidplatform.net@evil.example/","https://evil@appassets.androidplatform.net/","javascript:alert(1)","file:///android_asset/index.html","content://appassets.androidplatform.net/","https://appassets.androidplatform.net:444/","https://appassets.androidplatform.net./","https://evil.example/?host=appassets.androidplatform.net"};
        for(String p:attacks){if(EndpointPolicy.assetOrigin(p))throw new AssertionError(p);passed++;}
        System.out.println("EndpointPolicy: "+passed+"/"+passed+" assertions passed");
    }
}
