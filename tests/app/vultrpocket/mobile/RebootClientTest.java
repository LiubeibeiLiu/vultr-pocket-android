package app.vultrpocket.mobile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import javax.net.ssl.HttpsURLConnection;

public final class RebootClientTest {
    private static int passed;
    private static void check(boolean value,String name){if(!value)throw new AssertionError(name);passed++;}
    private static final class Fake extends HttpsURLConnection {
        final int status;final ByteArrayInputStream error;final ByteArrayOutputStream output=new ByteArrayOutputStream();int calls;boolean disconnected,timeout;
        Fake(URL url,int status,String body){super(url);this.status=status;error=new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));}
        public String getCipherSuite(){return "test";}public Certificate[] getLocalCertificates(){return null;}public Certificate[] getServerCertificates(){return new Certificate[0];}
        public void connect(){}public boolean usingProxy(){return false;}public void disconnect(){disconnected=true;}
        public int getResponseCode()throws IOException{calls++;if(timeout)throw new java.net.SocketTimeoutException();return status;}
        public OutputStream getOutputStream(){return output;}public InputStream getErrorStream(){return error;}
        public InputStream getInputStream(){throw new AssertionError("success body must not be parsed");}
    }
    private static RebootClient.Result run(int status,String body,boolean timeout,Fake[] saved){return new RebootClient(url->{saved[0]=new Fake(url,status,body);saved[0].timeout=timeout;return saved[0];}).request("unit-test-token","abc-123");}
    public static void main(String[] args){
        Fake[] saved=new Fake[1];RebootClient.Result ok=run(204,"",false,saved);Fake f=saved[0];
        check(ok.accepted(),"empty 204 success");check(f.getURL().toString().equals("https://api.vultr.com/v2/instances/abc-123/reboot"),"fixed origin and path");check(f.getRequestMethod().equals("POST"),"POST only");check(f.output.size()==0,"empty request body");check(!f.getInstanceFollowRedirects(),"redirects disabled");check(f.getConnectTimeout()==8000&&f.getReadTimeout()==8000,"bounded timeouts");check(f.getRequestProperty("Authorization").equals("Bearer unit-test-token"),"native authorization");check(f.calls==1&&f.disconnected,"single attempt and disconnect");
        for(int code:new int[]{200,202,302,503}){RebootClient.Result result=run(code,"",false,saved);check(!result.accepted()&&result.code.equals("reboot_result_unknown"),"uncertain result");check(saved[0].calls==1,"no retry");}
        RebootClient.Result forbidden=run(403,"permission denied",false,saved);check(forbidden.code.equals("upstream_403")&&!forbidden.accepted(),"permission denied");
        RebootClient.Result acl=run(401,"Unauthorized IP address: 192.0.2.4",false,saved);check(acl.code.equals("source_ip_denied")&&acl.sourceIp.equals("192.0.2.4"),"ACL classification");
        RebootClient.Result timeout=run(0,"",true,saved);check(timeout.code.equals("reboot_result_unknown")&&saved[0].calls==1&&saved[0].disconnected,"timeout not retried");
        run(400,new String(new char[6000]).replace('\0','x'),false,saved);check(saved[0].error.available()==1904,"error body bounded to 4096");
        final int[] opens={0};RebootClient blocked=new RebootClient(url->{opens[0]++;throw new AssertionError("network must not open");});
        check(blocked.request("","abc").code.equals("not_configured")&&opens[0]==0,"empty token blocked");
        try{blocked.request("unit-test-token","../reinstall");throw new AssertionError("invalid id accepted");}catch(IllegalArgumentException expected){check(opens[0]==0,"invalid id blocked before network");}
        System.out.println("RebootClient: "+passed+"/"+passed+" assertions passed");
    }
}
