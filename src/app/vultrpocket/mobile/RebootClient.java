package app.vultrpocket.mobile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;

final class RebootClient {
    interface Connections { HttpsURLConnection open(URL url) throws Exception; }
    static final class Result {
        final int status;final String code,sourceIp;
        Result(int status,String code,String sourceIp){this.status=status;this.code=code;this.sourceIp=sourceIp;}
        boolean accepted(){return RebootPolicy.accepted(status)&&code.isEmpty();}
    }
    private final Connections connections;
    RebootClient(){this(url->(HttpsURLConnection)url.openConnection());}
    RebootClient(Connections connections){this.connections=connections;}
    Result request(String credential,String instanceId){
        String path=RebootPolicy.path(instanceId);
        if(credential==null||credential.isEmpty())return new Result(0,"not_configured","");
        HttpsURLConnection connection=null;
        long deadline=System.nanoTime()+16000000000L;
        try{
            connection=connections.open(new URL("https://api.vultr.com/v2"+path));
            connection.setInstanceFollowRedirects(false);connection.setRequestMethod("POST");connection.setConnectTimeout(8000);connection.setReadTimeout(8000);
            connection.setRequestProperty("Authorization","Bearer "+credential);connection.setRequestProperty("User-Agent","VultrPocket-Android");
            connection.setDoOutput(true);connection.setFixedLengthStreamingMode(0);connection.getOutputStream().close();
            int status=connection.getResponseCode();if(RebootPolicy.accepted(status))return new Result(status,"","");
            String body="";InputStream error=connection.getErrorStream();if(error!=null)try(InputStream in=error;ByteArrayOutputStream buffer=new ByteArrayOutputStream()){
                byte[] chunk=new byte[1024];int n;while(buffer.size()<4096){long left=(deadline-System.nanoTime())/1000000L;if(left<=0)throw new java.net.SocketTimeoutException();connection.setReadTimeout((int)Math.min(8000,left));n=in.read(chunk,0,Math.min(chunk.length,4096-buffer.size()));if(n==-1)break;buffer.write(chunk,0,n);}body=new String(buffer.toByteArray(),StandardCharsets.UTF_8);
            }
            if(status>=500||(status>=200&&status<400))return new Result(status,"reboot_result_unknown","");
            return new Result(status,ApiErrorPolicy.classify(status,body),ApiErrorPolicy.sourceIp(body));
        }catch(Exception e){return new Result(0,"reboot_result_unknown","");}
        finally{if(connection!=null)connection.disconnect();}
    }
}
