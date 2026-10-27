package app.vultrpocket.mobile;

import android.os.SystemClock;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONArray;
import org.json.JSONObject;

final class VultrClient {
    private JSONObject cached;
    private final ReadLifecycle reads=new ReadLifecycle();
    private volatile HttpsURLConnection activeRead;
    static final class BackgroundPaused extends RuntimeException {}
    private long deadline;
    private JSONArray issues;
    private String token;
    private String lastStatusId="";private long lastStatusAttempt;private JSONObject lastStatus;
    private final JSONArray diagnosticLog = new JSONArray();
    private final Object logLock=new Object();
    private String deniedIp = "";
    private int lastHttpStatus;
    private static final Set<String> PATHS = new HashSet<>();
    static { Collections.addAll(PATHS, "/instances", "/account", "/account/bandwidth", "/billing/invoices", "/regions", "/plans"); }
    static boolean allowedPath(String p) { return EndpointPolicy.apiPath(p); }
    synchronized void clear() { cached = null; token = null; lastStatus=null;lastStatusId="";lastStatusAttempt=0; }
    private void closeActiveRead(){HttpsURLConnection connection=activeRead;if(connection!=null){Thread cancel=new Thread(()->{try{connection.disconnect();}catch(Exception ignored){}},"VultrReadCancel");cancel.setDaemon(true);cancel.start();}}
    void setForeground(boolean value){reads.foreground(value);if(!value)closeActiveRead();}
    void cancelReads(){reads.cancel();closeActiveRead();}
    boolean isForeground(){return reads.foreground();}
    synchronized JSONObject cachedSnapshot() throws Exception{if(cached!=null)try{if(!RetentionPolicy.snapshotValid(java.time.Instant.parse(cached.getString("updated_at")).toEpochMilli(),System.currentTimeMillis(),cached.optString("month_utc"),utc("yyyy-MM")))cached=null;}catch(Exception e){cached=null;}return cached==null?null:new JSONObject(cached.toString());}
    synchronized void invalidateAfterReboot() throws Exception{if(cached!=null)cached.put("stale",true);token=null;lastStatus=null;lastStatusId="";lastStatusAttempt=0;}
    synchronized void seed(JSONObject snapshot) throws Exception { if(cached==null)cached=new JSONObject(snapshot.toString()); }
    void clearLogs() { synchronized(logLock){while(diagnosticLog.length()>0)diagnosticLog.remove(0);} }
    JSONArray logs() throws Exception { synchronized(logLock){JSONArray out=new JSONArray();for(int n=diagnosticLog.length()-1;n>=0;n--)out.put(new JSONObject(diagnosticLog.getJSONObject(n).toString()));return out;} }
    int logCount() { synchronized(logLock){return diagnosticLog.length();} }
    private void appendLog(JSONObject entry){synchronized(logLock){diagnosticLog.put(entry);while(diagnosticLog.length()>80)diagnosticLog.remove(0);}}
    void recordLocalFailure(String code,String message) throws Exception {appendLog(new JSONObject().put("time",now()).put("section","connection").put("code",code).put("message",message));}
    synchronized JSONObject instanceStatus(String credential,String id) throws Exception {
        if(!RebootPolicy.instanceId(id))throw new IllegalArgumentException("invalid_instance");
        if(credential.isEmpty())throw new IllegalArgumentException("not_configured");
        long elapsed=SystemClock.elapsedRealtime();
        if(id.equals(lastStatusId)&&lastStatus!=null&&elapsed-lastStatusAttempt<5000)return new JSONObject(lastStatus.toString()).put("cached",true);
        token=credential;deadline=elapsed+12000;issues=new JSONArray();lastStatusAttempt=elapsed;lastStatusId=id;
        try{
            JSONObject raw=get("/instances/"+id,null).optJSONObject("instance");
            if(raw==null||!id.equals(text(raw,"id")))throw new Exception("missing_field");
            JSONObject current=pick(raw,"id","status","power_status","server_status");
            current.put("health",health(raw));
            if(cached!=null){JSONArray instances=cached.optJSONArray("instances");if(instances!=null)for(int n=0;n<instances.length();n++){JSONObject instance=instances.getJSONObject(n);if(id.equals(text(instance,"id")))for(String key:new String[]{"status","power_status","server_status","health"})instance.put(key,current.get(key));}}
            lastStatus=new JSONObject().put("ok",true).put("instance",current).put("fetched_at",now()).put("cached",false);return new JSONObject(lastStatus.toString());
        }catch(Exception e){if(e instanceof BackgroundPaused)throw (BackgroundPaused)e;String code=errorCode(e);issue("instance_status:"+id,code);lastStatus=new JSONObject().put("ok",false).put("error",ApiErrorPolicy.message(code)).put("retry_after_seconds",code.equals("upstream_429")?60:0);return new JSONObject(lastStatus.toString());}
        finally{token=null;}
    }
    private static String health(JSONObject raw){String status=text(raw,"status"),power=text(raw,"power_status"),server=text(raw,"server_status");return status.equals("active")&&power.equals("running")&&server.equals("ok")?"healthy":status.equals("active")&&power.equals("stopped")?"stopped":status.equals("pending")?"pending":"warning";}
    synchronized JSONObject rebootTarget(String id) throws Exception {
        if(!RebootPolicy.instanceId(id)||cached==null||cached.optBoolean("stale")||!cached.optBoolean("instances_known"))return null;
        long age=System.currentTimeMillis()-java.time.Instant.parse(cached.getString("updated_at")).toEpochMilli();if(age<0||age>300000)return null;
        JSONArray list=cached.optJSONArray("instances");if(list==null)return null;
        for(int n=0;n<list.length();n++){JSONObject target=list.getJSONObject(n);if(id.equals(target.optString("id"))&&RebootPolicy.targetReady(target.optString("status"),target.optString("power_status")))return new JSONObject(target.toString());}
        return null;
    }
    void recordReboot(RebootClient.Result result) throws Exception {
        if(result.accepted())return;
        JSONObject entry=new JSONObject().put("time",now()).put("section","reboot").put("code",result.code).put("message",ApiErrorPolicy.message(result.code));
        if(result.status>0)entry.put("http_status",result.status);if(!result.sourceIp.isEmpty())entry.put("source_ip",result.sourceIp);
        appendLog(entry);
    }
    private static String utc(String format) { SimpleDateFormat f = new SimpleDateFormat(format, Locale.US); f.setTimeZone(TimeZone.getTimeZone("UTC")); return f.format(new Date()); }
    private static String now() { return utc("yyyy-MM-dd'T'HH:mm:ss'Z'"); }
    static JSONObject emptySnapshot() throws Exception {
        JSONObject s = new JSONObject();
        s.put("currency","USD").put("mode", "device").put("month_utc", utc("yyyy-MM")).put("updated_at", now()).put("attempted_at", now()).put("cached", false).put("stale", false).put("retry_after_seconds", 0);
        s.put("instances", new JSONArray()).put("instances_known", false).put("invoices", new JSONArray()).put("invoices_known", false);
        s.put("account", JSONObject.NULL).put("account_bandwidth", JSONObject.NULL).put("issues", new JSONArray()); return s;
    }
    static boolean refreshSucceeded(JSONObject snapshot){JSONArray errors=snapshot.optJSONArray("issues");return RefreshPolicy.succeeded(snapshot.optBoolean("instances_known"),snapshot.optBoolean("cached"),snapshot.optBoolean("stale"),snapshot.optBoolean("refresh_paused"),errors==null?-1:errors.length());}
    synchronized JSONObject dashboard(String credential, boolean force) throws Exception {
        if (credential.isEmpty()) { JSONObject s = emptySnapshot(); s.getJSONArray("issues").put(new JSONObject().put("section", "connection").put("code", "not_configured").put("message", "点击设置添加 API Token")); return s; }
        if(cached!=null){try{long measured=java.time.Instant.parse(cached.getString("updated_at")).toEpochMilli();if(!RetentionPolicy.snapshotValid(measured,System.currentTimeMillis(),cached.optString("month_utc"),utc("yyyy-MM")))cached=null;}catch(Exception e){cached=null;}}
        token = credential; deadline = SystemClock.elapsedRealtime() + 30000; issues = new JSONArray();
        try {
            JSONObject s = emptySnapshot();
            JSONArray rawInstances = attemptList("/instances", "instances", "instances");
            boolean known = rawInstances != null; s.put("instances_known", known);
            JSONObject a = attemptObject("/account", "account", "account");
            if (a != null) s.put("account", pick(a, "balance", "pending_charges", "last_payment_date", "last_payment_amount"));
            JSONObject bw = attemptObject("/account/bandwidth", "bandwidth", "account_bandwidth");
            if (bw != null) {
                JSONObject current = bw.optJSONObject("current_month_to_date");
                if (current != null) s.put("account_bandwidth", new JSONObject().put("current_month_to_date", pick(current, "timestamp_start", "timestamp_end", "gb_in", "gb_out", "instance_bandwidth_credits", "free_bandwidth_credits", "purchased_bandwidth_credits", "overage", "overage_cost", "overage_unit_cost")));
                else issue("account_bandwidth", "missing_field");
            }
            JSONArray invoices = attemptList("/billing/invoices", "billing_invoices", "invoices");
            s.put("invoices_known", invoices != null);
            if (invoices != null) {
                List<JSONObject> sorted = new ArrayList<>();
                for (int k=0;k<invoices.length();k++) sorted.add(pick(invoices.getJSONObject(k), "id", "date", "description", "amount", "balance"));
                Collections.sort(sorted, (l,r)->r.optString("date").compareTo(l.optString("date")));
                JSONArray out = new JSONArray(); for (int k=0;k<Math.min(12, sorted.size());k++) out.put(sorted.get(k)); s.put("invoices", out);
            }
            JSONArray regions = attemptList("/regions", "regions", "regions"), plans = attemptList("/plans", "plans", "plans");
            JSONArray instances = new JSONArray();
            if (rawInstances != null) for (int k=0;k<rawInstances.length();k++) {
                JSONObject raw = rawInstances.getJSONObject(k);
                JSONObject i = pick(raw, "id", "label", "main_ip", "v6_main_ip", "region", "plan", "os", "ram", "vcpu_count", "disk", "status", "power_status", "server_status", "allowed_bandwidth");
                i.put("health", health(raw)).put("traffic", JSONObject.NULL).put("monthly_cost", JSONObject.NULL).put("city", "").put("country", "");
                if (regions != null) for (int j=0;j<regions.length();j++) { JSONObject r=regions.getJSONObject(j); if (text(r,"id").equals(text(raw,"region"))) { i.put("city",text(r,"city")).put("country",text(r,"country")); break; } }
                if (plans != null) for (int j=0;j<plans.length();j++) { JSONObject p=plans.getJSONObject(j); if (text(p,"id").equals(text(raw,"plan"))) { i.put("monthly_cost",number(p,"monthly_cost")); break; } }
                String id=text(raw,"id");
                JSONObject days=attemptObject("/instances/"+id+"/bandwidth", "bandwidth", "instance_bandwidth:"+id);
                if (days!=null) { try { i.put("traffic",aggregate(days,raw)); } catch(Exception e) { issue("instance_bandwidth:"+id,"invalid_data"); } }
                instances.put(i);
            }
            s.put("instances",instances).put("issues",issues);
            if (!known && cached!=null && cached.optString("month_utc").equals(utc("yyyy-MM"))) {
                JSONObject old=new JSONObject(cached.toString()); old.put("stale",true).put("cached",true).put("attempted_at",now()).put("issues",issues);s=old;
            } else if(cached!=null&&cached.optString("month_utc").equals(utc("yyyy-MM"))){
                boolean reused=false;
                for(String section:new String[]{"account","account_bandwidth"})if(s.isNull(section)&&!cached.isNull(section)){s.put(section,cached.get(section));reused=true;}
                if(!s.optBoolean("invoices_known")&&cached.optBoolean("invoices_known")){s.put("invoices",cached.getJSONArray("invoices")).put("invoices_known",true);reused=true;}
                JSONArray previous=cached.optJSONArray("instances");
                if(previous!=null)for(int n=0;n<instances.length();n++){JSONObject current=instances.getJSONObject(n);if(!current.isNull("traffic"))continue;for(int p=0;p<previous.length();p++){JSONObject old=previous.getJSONObject(p);if(text(current,"id").equals(text(old,"id"))&&text(current,"plan").equals(text(old,"plan"))&&current.optDouble("allowed_bandwidth",-1)==old.optDouble("allowed_bandwidth",-2)&&!old.isNull("traffic")){current.put("traffic",old.get("traffic"));reused=true;break;}}}
                if(reused)s.put("stale",true).put("cached",true).put("updated_at",cached.get("updated_at"));
            }
            cached=new JSONObject(s.toString());return s;
        } finally { token=null; }
    }
    static String text(JSONObject o,String key) { Object v=o.opt(key);return v instanceof String?(String)v:""; }
    static Object number(JSONObject o,String key) { Object v=o.opt(key);return v instanceof Number && Double.isFinite(((Number)v).doubleValue())?v:JSONObject.NULL; }
    private static JSONObject pick(JSONObject src,String... keys) throws Exception { JSONObject out=new JSONObject();for(String k:keys) { Object v=src.opt(k);out.put(k,v instanceof String||v instanceof Number||v instanceof Boolean?v:JSONObject.NULL); }return out; }
    private JSONObject get(String path,String cursor) throws Exception {
        long readTicket=reads.begin();if(readTicket<0)throw new BackgroundPaused();
        if (!allowedPath(path)) throw new Exception("blocked_path");
        long left=deadline-SystemClock.elapsedRealtime();if(left<=0)throw new Exception("timeout");
        String query=PATHS.contains(path)&&!path.equals("/account")&&!path.equals("/account/bandwidth")?"?per_page=100":"";
        if(path.matches("/instances/[a-zA-Z0-9-]+/bandwidth"))query="?date_range="+BandwidthSeries.DATE_RANGE_DAYS;
        if(cursor!=null&&!cursor.isEmpty())query+="&cursor="+URLEncoder.encode(cursor,"UTF-8");
        HttpsURLConnection c=(HttpsURLConnection)new URL("https://api.vultr.com/v2"+path+query).openConnection();
        c.setInstanceFollowRedirects(false);c.setRequestMethod("GET");c.setConnectTimeout((int)Math.min(8000,left));c.setReadTimeout((int)Math.min(8000,left));
        c.setRequestProperty("Authorization","Bearer "+token);c.setRequestProperty("Accept","application/json");c.setRequestProperty("User-Agent","VultrPocket-Android/"+AppBuild.VERSION);
        activeRead=c;
        try { if(!reads.valid(readTicket))throw new BackgroundPaused();int status=c.getResponseCode();if(status!=200){lastHttpStatus=status;String errorBody="";InputStream errorStream=c.getErrorStream();if(errorStream!=null)try(InputStream in=errorStream;ByteArrayOutputStream b=new ByteArrayOutputStream()){byte[] chunk=new byte[1024];int n;while(b.size()<4096&&(n=in.read(chunk,0,Math.min(chunk.length,4096-b.size())))!=-1)b.write(chunk,0,n);errorBody=new String(b.toByteArray(),StandardCharsets.UTF_8);}deniedIp=ApiErrorPolicy.sourceIp(errorBody);throw new Exception(ApiErrorPolicy.classify(status,errorBody));}
            try(InputStream in=c.getInputStream();ByteArrayOutputStream b=new ByteArrayOutputStream()) { byte[] chunk=new byte[8192];int n;while((n=in.read(chunk))!=-1){if(SystemClock.elapsedRealtime()>deadline)throw new Exception("timeout");if(b.size()+n>8*1024*1024)throw new Exception("response_too_large");b.write(chunk,0,n);}return new JSONObject(new String(b.toByteArray(),StandardCharsets.UTF_8)); }
        } finally { c.disconnect();if(activeRead==c)activeRead=null;if(!reads.valid(readTicket))throw new BackgroundPaused(); }
    }
    private JSONArray list(String path,String key) throws Exception { JSONArray all=new JSONArray();String cursor="";Set<String>seen=new HashSet<>();for(int page=0;page<100;page++){JSONObject obj=get(path,cursor);JSONArray items=obj.optJSONArray(key);if(items==null)throw new Exception("missing_field");for(int i=0;i<items.length();i++)all.put(items.get(i));JSONObject meta=obj.optJSONObject("meta"),links=meta==null?null:meta.optJSONObject("links");cursor=links==null?"":text(links,"next");if(cursor.isEmpty())return all;if(!seen.add(cursor))throw new Exception("pagination_loop");}throw new Exception("pagination_limit"); }
    private JSONArray attemptList(String path,String key,String section) { try{return list(path,key);}catch(Exception e){if(e instanceof BackgroundPaused)throw (BackgroundPaused)e;issue(section,errorCode(e));return null;} }
    private JSONObject attemptObject(String path,String key,String section) { try{JSONObject value=get(path,null).optJSONObject(key);if(value==null)throw new Exception("missing_field");return value;}catch(Exception e){if(e instanceof BackgroundPaused)throw (BackgroundPaused)e;issue(section,errorCode(e));return null;} }
    private static String errorCode(Exception e) { if(e instanceof java.net.SocketTimeoutException)return "timeout";String s=e.getMessage();if(s!=null && s.matches("source_ip_denied|upstream_[0-9]{3}|blocked_path|timeout|missing_field|pagination_loop|pagination_limit|response_too_large"))return s;return "network_or_format"; }
    private void issue(String section,String code) { try{JSONObject entry=new JSONObject().put("time",now()).put("section",section).put("code",code).put("message",ApiErrorPolicy.message(code));if(code.startsWith("upstream_")||code.equals("source_ip_denied"))entry.put("http_status",lastHttpStatus);if(code.equals("source_ip_denied")&&!deniedIp.isEmpty())entry.put("source_ip",deniedIp);issues.put(entry);appendLog(new JSONObject(entry.toString()));}catch(Exception ignored){} }
    private static JSONObject aggregate(JSONObject days,JSONObject instance) throws Exception {
        List<BandwidthSeries.Day> raw=new ArrayList<>();Iterator<String> keys=days.keys();
        while(keys.hasNext()){String date=keys.next();JSONObject d=days.getJSONObject(date);Object in=number(d,"incoming_bytes"),out=number(d,"outgoing_bytes");if(!(in instanceof Number)||!(out instanceof Number))throw new Exception("missing_bytes");long a=((Number)in).longValue(),b=((Number)out).longValue();if(((Number)in).doubleValue()!=a||((Number)out).doubleValue()!=b)throw new Exception("invalid_bytes");raw.add(new BandwidthSeries.Day(date,a,b));}
        BandwidthSeries.Result result=BandwidthSeries.aggregate(raw,java.time.LocalDate.parse(utc("yyyy-MM-dd")));JSONArray daily=new JSONArray();
        for(BandwidthSeries.Day day:result.daily)daily.put(new JSONObject().put("date",day.date.toString()).put("in_bytes",day.incoming).put("out_bytes",day.outgoing));
        if(daily.length()==0)throw new Exception("no_bandwidth_data");
        boolean hasMonth=result.monthDays>0;
        JSONObject t=new JSONObject().put("in_bytes",hasMonth?result.incoming:JSONObject.NULL).put("out_bytes",hasMonth?result.outgoing:JSONObject.NULL).put("total_bytes",hasMonth?Math.addExact(result.incoming,result.outgoing):JSONObject.NULL).put("reported_days",result.monthDays).put("daily",daily).put("requested_days",BandwidthSeries.DATE_RANGE_DAYS).put("plan_remaining_gb_estimate",JSONObject.NULL).put("plan_percent_estimate",JSONObject.NULL);
        Object cap=number(instance,"allowed_bandwidth");if(hasMonth&&cap instanceof Number&&((Number)cap).doubleValue()>0){double total=((Number)cap).doubleValue(),used=result.outgoing/1e9;t.put("plan_remaining_gb_estimate",Math.max(0,total-used)).put("plan_percent_estimate",used/total*100);}
        return t;
    }
}


