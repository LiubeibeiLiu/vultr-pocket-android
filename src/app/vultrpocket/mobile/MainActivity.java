package app.vultrpocket.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.InputType;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.widget.TextView;
import android.widget.Button;
import android.widget.ScrollView;
import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;

public final class MainActivity extends Activity {
    private static final String ORIGIN = "appassets.androidplatform.net";
    private static final Set<String> ASSETS = new HashSet<>(Arrays.asList("index.html", "app.js", "style.css", "icon.svg", "icon-192.png", "icon-512.png", "manifest.webmanifest", "fonts/Tinos-Regular.ttf", "fonts/Tinos-Bold.ttf"));
    private WebView web;
    private Vault vault;
    private RefreshGate refreshGate;
    private DeviceUnlock deviceUnlock;
    private final VultrClient vultr = new VultrClient();
    private final RebootClient rebootClient=new RebootClient();
    private final RebootPolicy rebootPolicy=new RebootPolicy();
    private final AtomicBoolean rebootPending=new AtomicBoolean();
    private volatile int credentialGeneration;
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(8));
    private final ThreadPoolExecutor localWorker = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(8));
    private volatile long unlockedUntil;
    private long lastStop, loginWindow;
    private int loginAttempts;
    private boolean destroyed;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        try { vault = new Vault(this); } catch (Exception e) { new AlertDialog.Builder(this).setMessage("无法初始化安全存储，请重新安装应用").setPositiveButton("关闭", (d,w)->finish()).setCancelable(false).show(); return; }
        refreshGate=new RefreshGate(new RefreshGate.Store(){public RefreshGate.Record load(){return vault.refreshRecord();}public void save(RefreshGate.Record record)throws Exception{vault.saveRefreshRecord(record);}});
        deviceUnlock=new DeviceUnlock(this);
        LinearLayout container = new LinearLayout(this); container.setBackgroundColor(Color.rgb(239,239,243));
        container.setOnApplyWindowInsetsListener((view, insets) -> {
            if (android.os.Build.VERSION.SDK_INT >= 30) { android.graphics.Insets b=insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());view.setPadding(b.left,b.top,b.right,b.bottom); }
            else { view.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom()); }
            return insets;
        });
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        web=new WebView(this);web.setBackgroundColor(Color.rgb(239,239,243));container.addView(web,new LinearLayout.LayoutParams(-1,-1));setContentView(container);
        WebView.setWebContentsDebuggingEnabled(false);
        WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(false);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);settings.setSupportMultipleWindows(false);settings.setJavaScriptCanOpenWindowsAutomatically(false);settings.setSafeBrowsingEnabled(true);settings.setSaveFormData(false);
        CookieManager.getInstance().setAcceptCookie(false);
        web.addJavascriptInterface(new Bridge(),"PanelNative");
        web.setWebChromeClient(new WebChromeClient(){@Override public void onPermissionRequest(PermissionRequest request){request.deny();}});
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return !trusted(request.getUrl());}
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
                Uri u=request.getUrl();if(!trusted(u)||!request.getMethod().equals("GET"))return blocked();
                String p=u.getPath(),name=p!=null&&p.startsWith("/assets/")?p.substring(8):p!=null&&p.startsWith("/")?p.substring(1):"";
                if(!ASSETS.contains(name))return blocked();
                try {
                    String mime=name.endsWith(".js")?"application/javascript":name.endsWith(".css")?"text/css":name.endsWith(".ttf")?"font/ttf":name.endsWith(".svg")?"image/svg+xml":name.endsWith(".png")?"image/png":name.endsWith(".webmanifest")?"application/manifest+json":"text/html";
                    WebResourceResponse response=new WebResourceResponse(mime,"UTF-8",getAssets().open("web/"+name));
                    java.util.Map<String,String> headers=new java.util.HashMap<>();headers.put("Cache-Control","no-store");headers.put("X-Content-Type-Options","nosniff");headers.put("Content-Security-Policy","default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self'; connect-src 'none'; frame-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'");response.setResponseHeaders(headers);return response;
                } catch(Exception e){return blocked();}
            }
        });
        web.loadUrl("https://"+ORIGIN+"/assets/index.html");
        if(android.os.Build.VERSION.SDK_INT>=33)getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,()->navigateBack());
    }
    static boolean trusted(Uri u){return u!=null&&EndpointPolicy.assetOrigin(u.toString());}
    private static WebResourceResponse blocked(){return new WebResourceResponse("text/plain","UTF-8",403,"Forbidden",java.util.Collections.emptyMap(),new ByteArrayInputStream(new byte[0]));}
    private boolean authenticated(){if(vault==null)return false;if(unlockedUntil>SystemClock.elapsedRealtime()&&(vault.deviceUnlock()||!vault.rememberLogin()||vault.loginTicketValidNow()))return true;if(!vault.deviceUnlock()&&vault.restoreLogin()){unlockedUntil=SystemClock.elapsedRealtime()+8*60*60*1000L;return true;}return false;}
    private final class Bridge {
        @JavascriptInterface public void request(String id,String action,String body){
            if(id==null||!id.matches("[a-zA-Z0-9-]{1,64}")||action==null||body==null||body.length()>8192)return;
            boolean refreshPreference=false;try{refreshPreference=action.equals("preferences")&&new JSONObject(body).has("refresh_minutes");}catch(Exception ignored){}
            if(action.equals("logout"))vultr.cancelReads();
            try {(BridgeDispatch.local(action,refreshPreference)?localWorker:worker).execute(()->handle(id,action,body));} catch(java.util.concurrent.RejectedExecutionException e){reply(id,429,null,"请稍后重试");}
        }
    }
    private void handle(String id,String action,String body){
        try {
            JSONObject input=new JSONObject(body);
            if(action.equals("session")){boolean auth=authenticated();JSONObject snapshot=null;if(auth)try{snapshot=vault.snapshot(vault.token());if(snapshot!=null)vultr.seed(snapshot);}catch(Exception e){vultr.recordLocalFailure("snapshot_storage","本机缓存暂时无法读取，将重新获取数据");}reply(id,200,refreshMetadata(new JSONObject().put("authenticated",auth).put("password_setup_required",vault.needsPasswordSetup()).put("retry_login_restore",!auth&&!vault.deviceUnlock()&&vault.rememberLogin()&&vault.retryLoginRestore()).put("version",AppBuild.VERSION).put("remember_login",vault.rememberLogin()).put("device_unlock_enabled",vault.deviceUnlock()).put("device_unlock_available",deviceUnlock.available()).put("refresh_minutes",vault.refreshMinutes()).put("reboot_enabled",auth&&vault.rebootEnabled()).put("snapshot",snapshot==null?JSONObject.NULL:snapshot),auth),null);return;}
            if(action.equals("setup_password")){
                if(!vault.needsPasswordSetup()){reply(id,409,null,"访问密码已设置，请直接登录");return;}
                String password=input.optString("password",""),confirmation=input.optString("confirmation","");
                if(!PasswordPolicy.acceptable(password,confirmation)){reply(id,400,null,"密码须为8至512位，且两次输入相同");return;}
                vault.initializePassword(password,confirmation);loginAttempts=0;unlockedUntil=SystemClock.elapsedRealtime()+8*60*60*1000L;
                reply(id,200,new JSONObject().put("ok",true),null);return;
            }
            if(action.equals("login")){
                if(vault.needsPasswordSetup()){reply(id,409,null,"请先创建访问密码");return;}
                long now=SystemClock.elapsedRealtime();if(now-loginWindow>=60000){loginWindow=now;loginAttempts=0;}if(++loginAttempts>5){reply(id,429,null,"登录尝试过多，请一分钟后重试");return;}
                String password=input.optString("password","");if(password.length()>512||!vault.checkPassword(password)){reply(id,401,null,"访问密码不正确");return;}
                boolean setup=input.optBoolean("setup_system",false);if(setup&&!deviceUnlock.available()){reply(id,409,null,"请先设置手机系统验证");return;}
                boolean remember=!setup&&(input.optBoolean("remember_login",true)||!vault.deviceUnlock());vault.setRememberLogin(remember);if(remember)vault.setDeviceUnlock(false);loginAttempts=0;unlockedUntil=SystemClock.elapsedRealtime()+8*60*60*1000L;
                if(setup){runOnUiThread(()->authenticateDevice(id,true,true));return;}reply(id,200,new JSONObject().put("ok",true),null);return;
            }
            if(action.equals("logout")){credentialGeneration++;unlockedUntil=0;vault.clearLoginTicket();vault.clearSnapshot();vultr.clear();vultr.clearLogs();reply(id,200,new JSONObject().put("ok",true),null);return;}
            if(action.equals("device_auth")){if(!vault.deviceUnlock()){reply(id,409,null,"请在登录页输入密码并启用系统验证");return;}runOnUiThread(()->authenticateDevice(id,false));return;}
            if(!authenticated()){reply(id,401,null,"请先登录");return;}
            if(action.equals("reboot_instance")){requestReboot(id,input.optString("instance_id",""));return;}
            if(action.equals("instance_status")){String instanceId=input.optString("instance_id","");if(!RebootPolicy.instanceId(instanceId)){reply(id,400,null,"实例无效");return;}JSONObject result;if(!vultr.isForeground())result=new JSONObject().put("ok",false).put("paused",true);else try{result=vultr.instanceStatus(vault.token(),instanceId);}catch(VultrClient.BackgroundPaused e){result=new JSONObject().put("ok",false).put("paused",true);}if(!authenticated()){reply(id,401,null,"请重新登录");return;}reply(id,200,result,null);return;}
            if(action.equals("copy_ip")){
                String address=input.optString("address","");if(!AddressPolicy.copyable(address)){reply(id,400,null,"IP 地址无效");return;}
                runOnUiThread(()->{if(!authenticated()){reply(id,401,null,"请重新登录");return;}try{ClipboardManager clipboard=getSystemService(ClipboardManager.class);if(clipboard==null)throw new Exception("clipboard_unavailable");clipboard.setPrimaryClip(ClipData.newPlainText("IP",address));reply(id,200,new JSONObject().put("ok",true).put("system_feedback",android.os.Build.VERSION.SDK_INT>=33),null);}catch(Exception e){reply(id,500,null,"无法复制，请长按 IP 地址选择");}});return;
            }
            if(action.equals("dashboard")){
                String credential=vault.token(),binding=vault.refreshBinding();JSONObject snapshot;
                if(!vultr.isForeground())snapshot=pausedDashboard(credential).put("refresh_paused",true);
                else if(!credential.isEmpty()&&!refreshGate.allowed(binding,System.currentTimeMillis(),SystemClock.elapsedRealtime(),bootId(),input.optBoolean("refresh",false)))snapshot=pausedDashboard(credential).put("refresh_blocked",true);
                else try{snapshot=vultr.dashboard(credential,input.optBoolean("refresh",false));}catch(VultrClient.BackgroundPaused e){snapshot=pausedDashboard(credential).put("refresh_paused",true);}
                if(!authenticated()){reply(id,401,null,"请重新登录");return;}if(VultrClient.refreshSucceeded(snapshot)&&!credential.isEmpty())refreshGate.succeeded(binding,System.currentTimeMillis(),SystemClock.elapsedRealtime(),bootId());try{vault.saveSnapshot(snapshot,credential);}catch(Exception ignored){}reply(id,200,refreshMetadata(snapshot,true),null);return;
            }
            if(action.equals("settings")){reply(id,200,settingsData(),null);return;}
            if(action.equals("logs")){reply(id,200,new JSONObject().put("entries",vultr.logs()),null);return;}
            if(action.equals("clear_logs")){vultr.clearLogs();reply(id,200,new JSONObject().put("ok",true),null);return;}
            if(action.equals("preferences")){
                if(input.has("refresh_minutes")){Object raw=input.opt("refresh_minutes");if(!(raw instanceof Number)||((Number)raw).doubleValue()!=((Number)raw).intValue()||!RefreshPolicy.validMinutes(((Number)raw).intValue())){reply(id,400,null,"刷新时间无效");return;}vault.setRefreshMinutes(((Number)raw).intValue());reply(id,200,settingsData(),null);return;}
                if(input.opt("reboot_enabled") instanceof Boolean){vault.setRebootEnabled(input.getBoolean("reboot_enabled"));credentialGeneration++;reply(id,200,settingsData(),null);return;}
                String mode=input.optString("login_mode","");
                if(mode.equals("remember")){vault.setRememberLogin(true);vault.setDeviceUnlock(false);reply(id,200,settingsData(),null);return;}
                if(mode.equals("system")){runOnUiThread(()->authenticateDevice(id,true));return;}
                reply(id,400,null,"设置无效");return;
            }
            if(action.equals("edit_token")||action.equals("edit_password")||action.equals("clear_token")){
                reply(id,200,new JSONObject().put("ok",true),null);runOnUiThread(()->{if(!authenticated()||destroyed)return;if(action.equals("edit_token"))editToken();else if(action.equals("edit_password"))editPassword();else PanelDialogs.form(this,"清除 Token","清除本机保存的 API Token？",new EditText[0],"清除",(dialog,error)->{dialog.dismiss();submitSetting(()->vault.clearToken());});});return;
            }
            reply(id,404,null,"不支持此操作");
        }catch(Exception e){reply(id,500,null,"操作失败，请重试");}
    }
    private void reply(String id,int status,JSONObject value,String error){
        try {JSONObject response=new JSONObject().put("status",status).put("data",value==null?JSONObject.NULL:value).put("error",error==null?JSONObject.NULL:error);
            String script="window.__nativeReply&&window.__nativeReply("+JSONObject.quote(id)+","+response+")";
            runOnUiThread(()->{if(!destroyed&&web!=null)web.evaluateJavascript(script,null);});
        }catch(Exception ignored){}
    }
    private long bootId(){try{return android.provider.Settings.Global.getInt(getContentResolver(),android.provider.Settings.Global.BOOT_COUNT,-1);}catch(Exception e){return -1;}}
    private JSONObject refreshMetadata(JSONObject data,boolean auth) throws Exception {
        long remaining=0,succeededAt=0;
        if(auth)try{String binding=vault.refreshBinding();if(!binding.isEmpty()){remaining=refreshGate.remaining(binding,System.currentTimeMillis(),SystemClock.elapsedRealtime(),bootId());succeededAt=refreshGate.lastSuccessAt(binding);}}catch(Exception e){remaining=RefreshPolicy.MIN_INTERVAL_MS;}
        return data.put("refresh_after_ms",remaining).put("refresh_measured_at_ms",System.currentTimeMillis()).put("last_refresh_success_at",succeededAt>0?java.time.Instant.ofEpochMilli(succeededAt).toString():JSONObject.NULL);
    }
    private JSONObject pausedDashboard(String credential) throws Exception {
        JSONObject snapshot=vultr.cachedSnapshot();if(snapshot==null)snapshot=vault.snapshot(credential);
        if(snapshot==null)snapshot=VultrClient.emptySnapshot().put("updated_at",JSONObject.NULL).put("attempted_at",JSONObject.NULL).put("stale",true);
        return snapshot.put("cached",true);
    }
    private JSONObject settingsData() throws Exception {
        return refreshMetadata(new JSONObject().put("ok",true).put("version",AppBuild.VERSION).put("compiled_at",AppBuild.COMPILED_AT).put("build_type","Release").put("token_set",vault.hasToken()).put("remember_login",vault.rememberLogin()).put("device_unlock_enabled",vault.deviceUnlock()).put("device_unlock_available",deviceUnlock.available()).put("refresh_minutes",vault.refreshMinutes()).put("reboot_enabled",vault.rebootEnabled()).put("log_count",vultr.logCount()),true);
    }
    private void requestReboot(String requestId,String instanceId) throws Exception {
        if(!vault.rebootEnabled()){reply(requestId,403,null,"实例重启功能未开启");return;}
        JSONObject target=vultr.rebootTarget(instanceId);if(target==null){reply(requestId,409,null,ApiErrorPolicy.message("reboot_target_unknown"));return;}
        if(!rebootPending.compareAndSet(false,true)){reply(requestId,409,null,"请先完成当前重启操作");return;}
        final int generation=credentialGeneration;final long opened=SystemClock.elapsedRealtime();
        runOnUiThread(()->{
            if(destroyed||isFinishing()){rebootPending.set(false);reply(requestId,409,null,"请返回应用后重试");return;}
            AtomicBoolean submitted=new AtomicBoolean();
            Dialog dialog=PanelDialogs.reboot(this,RebootPolicy.stateLabel(target.optString("status"),target.optString("power_status"),target.optString("server_status")),RebootPolicy.stateNotice(target.optString("status"),target.optString("power_status"),target.optString("server_status")),(window,error)->{
                if(!submitted.compareAndSet(false,true))return;window.dismiss();
                try{worker.execute(()->{
                    try{
                        if(!authenticated()||generation!=credentialGeneration||!vault.rebootEnabled()||SystemClock.elapsedRealtime()-opened>120000){reply(requestId,409,null,"设置或登录状态已变化，请重新确认");return;}
                        JSONObject current=vultr.rebootTarget(instanceId);if(current==null||!current.optString("label").equals(target.optString("label"))||!current.optString("main_ip").equals(target.optString("main_ip"))){reply(requestId,409,null,ApiErrorPolicy.message("reboot_target_unknown"));return;}
                        if(!rebootPolicy.acquire(instanceId,SystemClock.elapsedRealtime())){reply(requestId,429,null,ApiErrorPolicy.message("reboot_cooldown"));return;}
                        RebootClient.Result result=rebootClient.request(vault.token(),instanceId);vultr.recordReboot(result);
                        if(result.accepted()){vultr.invalidateAfterReboot();try{vault.clearSnapshot();}catch(Exception ignored){}reply(requestId,200,new JSONObject().put("accepted",true),null);}
                        else reply(requestId,result.status>=400&&result.status<500?result.status:502,null,ApiErrorPolicy.message(result.code));
                    }catch(Exception e){reply(requestId,500,null,"无法完成重启操作，请刷新状态后查看日志");}
                    finally{rebootPending.set(false);}
                });}catch(java.util.concurrent.RejectedExecutionException e){rebootPending.set(false);reply(requestId,429,null,"请稍后重试");}
            });
            dialog.setOnDismissListener(d->{if(!submitted.get()){rebootPending.set(false);try{reply(requestId,200,new JSONObject().put("cancelled",true),null);}catch(Exception ignored){}}});
        });
    }
    private void authenticateDevice(String id,boolean enabling){
        authenticateDevice(id,enabling,false);
    }
    private void authenticateDevice(String id,boolean enabling,boolean lockOnError){
        if(destroyed||isFinishing()){if(lockOnError)unlockedUntil=0;reply(id,409,null,"请返回应用后重试");return;}
        if(enabling&&!authenticated()){reply(id,401,null,"请先登录");return;}
        deviceUnlock.start(new DeviceUnlock.Result(){
            @Override public void success(){try{worker.execute(()->{try{if(destroyed)return;if(enabling){if(!authenticated()){reply(id,401,null,"请重新登录");return;}vault.setRememberLogin(false);vault.setDeviceUnlock(true);reply(id,200,settingsData(),null);}else{if(!vault.deviceUnlock()){reply(id,409,null,"手机验证已关闭");return;}unlockedUntil=SystemClock.elapsedRealtime()+8*60*60*1000L;loginAttempts=0;if(vault.rememberLogin())vault.rememberCurrentLogin();reply(id,200,new JSONObject().put("ok",true),null);}}catch(Exception e){if(lockOnError)unlockedUntil=0;reply(id,500,null,"无法完成系统验证，请使用登录密码");}});}catch(java.util.concurrent.RejectedExecutionException e){reply(id,429,null,"请稍后重试");}}
            @Override public void error(String message){if(lockOnError)unlockedUntil=0;reply(id,409,null,message);}
        });
    }
    private void editToken(){
        EditText input=PanelDialogs.field(this,"输入 API Token");
        PanelDialogs.form(this,"API Token","",new EditText[]{input},"保存",(dialog,error)->{
            String token=input.getText().toString().trim();if(token.length()<16||token.length()>4096||token.matches("(?s).*\\s.*")){error.setText("请输入有效 Token");return;}dialog.dismiss();submitSetting(()->vault.saveToken(token));
        });
    }
    private void editPassword(){
        EditText old=PanelDialogs.field(this,"当前密码"),fresh=PanelDialogs.field(this,"新密码（至少8字符）"),again=PanelDialogs.field(this,"确认新密码");
        PanelDialogs.form(this,"密码修改","",new EditText[]{old,fresh,again},"保存",(dialog,error)->{
            String previous=old.getText().toString(),next=fresh.getText().toString();if(!PasswordPolicy.acceptable(next,again.getText().toString())){error.setText("两次密码须相同，且至少8字符");return;}dialog.dismiss();submitSetting(()->{if(!vault.checkPassword(previous))throw new Exception("wrong_password");vault.changePassword(next);});
        });
    }
    private interface Change{void execute()throws Exception;}
    private void submitSetting(Change change){vultr.cancelReads();
        try {worker.execute(()->{try{if(!authenticated())return;change.execute();credentialGeneration++;vultr.clear();runOnUiThread(()->{if(!destroyed){Toast.makeText(this,"已保存",Toast.LENGTH_SHORT).show();web.evaluateJavascript("window.__nativeSettingsSaved&&window.__nativeSettingsSaved()",null);}});}catch(Exception e){runOnUiThread(()->Toast.makeText(this,"保存失败，请检查输入",Toast.LENGTH_SHORT).show());}});}catch(java.util.concurrent.RejectedExecutionException e){Toast.makeText(this,"请稍后重试",Toast.LENGTH_SHORT).show();}
    }
    @Override protected void onStart(){super.onStart();vultr.setForeground(true);if(web!=null)web.resumeTimers();}
    @Override protected void onStop(){vultr.setForeground(false);if(web!=null){web.onPause();web.pauseTimers();}super.onStop();lastStop=SystemClock.elapsedRealtime();if(deviceUnlock!=null)deviceUnlock.cancel();}
    @Override protected void onActivityResult(int request,int result,Intent intent){super.onActivityResult(request,result,intent);if(deviceUnlock!=null)deviceUnlock.activityResult(request,result);}
    @Override protected void onResume(){super.onResume();if(web!=null)web.onResume();long background=lastStop>0?SystemClock.elapsedRealtime()-lastStop:0;lastStop=0;if(RetentionPolicy.resumeNeedsReload(background,vault!=null&&vault.rememberLogin(),vault!=null&&vault.deviceUnlock(),vault!=null&&vault.loginTicketValidNow())){unlockedUntil=0;vultr.clear();if(web!=null)web.reload();}}
    private void navigateBack(){if(web!=null&&web.canGoBack())web.goBack();else finish();}
    @Override public void onBackPressed(){navigateBack();}
    @Override protected void onDestroy(){destroyed=true;unlockedUntil=0;if(deviceUnlock!=null)deviceUnlock.destroy();worker.shutdownNow();localWorker.shutdownNow();if(web!=null){web.removeJavascriptInterface("PanelNative");web.destroy();web=null;}super.onDestroy();}
}





