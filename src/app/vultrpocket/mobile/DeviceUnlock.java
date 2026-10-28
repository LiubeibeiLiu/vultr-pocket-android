package app.vultrpocket.mobile;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Intent;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Build;
import android.os.CancellationSignal;

final class DeviceUnlock {
    interface Result { void success(); void error(String message); }
    private static final int REQUEST=8247;
    private final Activity activity;
    private Result pending;
    private CancellationSignal cancellation;
    private boolean credentialPending;
    DeviceUnlock(Activity activity){this.activity=activity;}
    boolean available(){KeyguardManager k=activity.getSystemService(KeyguardManager.class);return k!=null&&k.isDeviceSecure();}
    void start(Result result){
        if(pending!=null){result.error("系统验证正在进行");return;}
        if(!available()){result.error("请先在手机系统中设置指纹、人脸或锁屏凭证");return;}
        pending=result;credentialPending=false;
        if(Build.VERSION.SDK_INT>=28)prompt();else credential();
    }
    private void prompt(){
        try{
            cancellation=new CancellationSignal();
            // Android requires a non-empty title. A space keeps the app-supplied title visually blank.
            BiometricPrompt.Builder builder=new BiometricPrompt.Builder(activity).setTitle(" ");
            if(Build.VERSION.SDK_INT>=30)builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK|BiometricManager.Authenticators.DEVICE_CREDENTIAL);
            else builder.setNegativeButton(I18n.text("使用锁屏密码"),activity::runOnUiThread,(d,w)->credential());
            builder.build().authenticate(cancellation,activity::runOnUiThread,new BiometricPrompt.AuthenticationCallback(){
                @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result){complete(true,"");}
                @Override public void onAuthenticationError(int code,CharSequence text){
                    if(credentialPending||pending==null)return;
                    if(code==BiometricPrompt.BIOMETRIC_ERROR_HW_NOT_PRESENT||code==BiometricPrompt.BIOMETRIC_ERROR_NO_BIOMETRICS||code==BiometricPrompt.BIOMETRIC_ERROR_HW_UNAVAILABLE)credential();
                    else complete(false,"验证已取消或未通过，可使用登录密码");
                }
            });
        }catch(Exception e){credential();}
    }
    private void credential(){
        if(pending==null||credentialPending)return;
        try{KeyguardManager k=activity.getSystemService(KeyguardManager.class);Intent intent=k==null?null:k.createConfirmDeviceCredentialIntent(" ",null);
            if(intent==null){complete(false,"手机未设置锁屏验证");return;}
            credentialPending=true;activity.startActivityForResult(intent,REQUEST);
        }catch(Exception e){complete(false,"无法打开系统验证，请使用登录密码");}
    }
    boolean activityResult(int request,int result){if(request!=REQUEST)return false;if(credentialPending)complete(result==Activity.RESULT_OK,"验证已取消，可使用登录密码");return true;}
    void cancel(){if(!credentialPending&&cancellation!=null)cancellation.cancel();}
    void destroy(){if(cancellation!=null)cancellation.cancel();pending=null;credentialPending=false;}
    private void complete(boolean success,String message){Result result=pending;pending=null;credentialPending=false;cancellation=null;if(result!=null){if(success)result.success();else result.error(message);}}
}
