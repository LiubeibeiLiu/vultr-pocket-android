package app.vultrpocket.mobile;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import org.json.JSONObject;

final class Vault {
    private static final String ALIAS = "vultr-pocket-api-token-v1";
    private static final String LOGIN_ALIAS = "vultr-pocket-login-v1";
    private static final String SNAPSHOT_ALIAS = "vultr-pocket-snapshot-v1";
    private static final int ITERATIONS = 210000;
    private final SharedPreferences prefs;
    private final LoginTicketStore loginTickets;
    Vault(Context context) throws Exception {
        prefs = context.getSharedPreferences("private-vault", Context.MODE_PRIVATE);
        loginTickets=new LoginTicketStore(new LoginTicketStore.Backend(){
            public String read(String name){return prefs.getString(name,"");}
            public long seen(){return prefs.getLong("login_seen_at",0);}
            public void save(String cipher,String iv,long seen)throws Exception{if(!prefs.edit().putString("login_cipher",cipher).putString("login_iv",iv).putLong("login_seen_at",seen).commit())throw new Exception("save_failed");}
            public void seen(long value)throws Exception{if(!prefs.edit().putLong("login_seen_at",value).commit())throw new Exception("save_failed");}
            public SecretKey key()throws Exception{return Vault.this.key(LOGIN_ALIAS);}
        },plain->{JSONObject ticket=new JSONObject(new String(plain,StandardCharsets.UTF_8));if(ticket.optInt("version")!=2)throw new Exception("invalid_version");return new LoginTicketStore.Claims(ticket.getString("password_hash"),ticket.getLong("issued_at"),ticket.getLong("expires_at"));});
    }
    synchronized boolean needsPasswordSetup(){return PasswordPolicy.needsSetup(prefs.getString("password_hash",""),prefs.getString("password_salt",""));}
    synchronized void initializePassword(String password,String confirmation)throws Exception{
        if(!needsPasswordSetup())throw new IllegalStateException("already_configured");
        if(!PasswordPolicy.acceptable(password,confirmation))throw new IllegalArgumentException("invalid_password");
        changePassword(password);
    }

    synchronized boolean checkPassword(String password) throws Exception {
        if(needsPasswordSetup())return false;
        byte[] salt = Base64.decode(prefs.getString("password_salt", ""), Base64.NO_WRAP);
        byte[] expected = Base64.decode(prefs.getString("password_hash", ""), Base64.NO_WRAP);
        byte[] actual = derive(password, salt);
        try { return MessageDigest.isEqual(expected, actual); } finally { Arrays.fill(actual, (byte)0); }
    }
    synchronized void changePassword(String password) throws Exception {
        boolean systemMode=deviceUnlock();
        byte[] salt = new byte[24]; new SecureRandom().nextBytes(salt);
        byte[] hash = derive(password, salt);
        try { if (!prefs.edit().putString("password_hash", b64(hash)).putString("password_salt", b64(salt)).commit()) throw new Exception("save_failed"); }
        finally { Arrays.fill(hash, (byte)0); }
        clearLoginTicket();clearSnapshot();setDeviceUnlock(systemMode);
        if(rememberLogin())rememberCurrentLogin();
    }
    private static byte[] derive(String password, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); } finally { spec.clearPassword(); }
    }
    private SecretKey key() throws Exception { return key(ALIAS); }
    private SecretKey key(String alias) throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
        if (ks.containsAlias(alias)) return (SecretKey)ks.getKey(alias, null);
        KeyGenerator g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        g.init(new KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setRandomizedEncryptionRequired(true).build());
        return g.generateKey();
    }
    synchronized boolean rememberLogin() { return prefs.getBoolean("remember_login",true); }
    synchronized void setRememberLogin(boolean enabled) throws Exception {
        if(!prefs.edit().putBoolean("remember_login",enabled).commit())throw new Exception("save_failed");
        if(enabled)rememberCurrentLogin();else clearLoginTicket();
    }
    synchronized void rememberCurrentLogin() throws Exception {
        loginTickets.issue(prefs.getString("password_hash",""),System.currentTimeMillis());
    }
    synchronized boolean restoreLogin() {
        if(!rememberLogin()||!prefs.contains("login_cipher"))return false;
        return loginTickets.restore(prefs.getString("password_hash",""),System.currentTimeMillis());
    }
    synchronized void clearLoginTicket() throws Exception {
        loginTickets.forget();
        if(!prefs.edit().remove("login_cipher").remove("login_iv").remove("login_seen_at").commit())throw new Exception("save_failed");
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);if(ks.containsAlias(LOGIN_ALIAS))ks.deleteEntry(LOGIN_ALIAS);
    }
    synchronized boolean loginTicketValidNow(){return loginTickets.valid(System.currentTimeMillis());}
    synchronized boolean retryLoginRestore(){return "secure_storage_unavailable".equals(loginTickets.failure());}
    synchronized boolean deviceUnlock() { return !prefs.getString("device_unlock_hash","").isEmpty()&&prefs.getString("device_unlock_hash","").equals(prefs.getString("password_hash","")); }
    synchronized void setDeviceUnlock(boolean enabled) throws Exception {if(!prefs.edit().putString("device_unlock_hash",enabled?prefs.getString("password_hash",""):"").commit())throw new Exception("save_failed");}
    synchronized boolean hasToken() { return prefs.contains("token_cipher"); }
    synchronized RefreshGate.Record refreshRecord(){if(prefs.getInt("refresh_success_version",0)!=1)return null;String fingerprint=prefs.getString("refresh_binding","");return fingerprint.isEmpty()?null:new RefreshGate.Record(fingerprint,prefs.getLong("refresh_attempted_at",0),prefs.getLong("refresh_anchor_wall",0),prefs.getLong("refresh_anchor_elapsed",0),prefs.getLong("refresh_boot",-1));}
    synchronized void saveRefreshRecord(RefreshGate.Record record) throws Exception {if(!prefs.edit().putInt("refresh_success_version",1).putString("refresh_binding",record.binding).putLong("refresh_attempted_at",record.succeededAt).putLong("refresh_anchor_wall",record.wall).putLong("refresh_anchor_elapsed",record.elapsed).putLong("refresh_boot",record.boot).commit())throw new Exception("save_failed");}
    synchronized String refreshBinding() throws Exception {if(!hasToken())return "";String fingerprint=prefs.getString("token_binding","");return fingerprint.isEmpty()?binding(token()):fingerprint;}
    synchronized boolean rebootEnabled(){return prefs.getBoolean("reboot_enabled",false);}
    synchronized String language(){return LanguagePolicy.normalize(prefs.getString("language","zh-CN"));}
    synchronized void setLanguage(String language)throws Exception{if(!LanguagePolicy.supported(language))throw new IllegalArgumentException("invalid_language");if(!prefs.edit().putString("language",language).commit())throw new Exception("save_failed");}
    synchronized int refreshMinutes(){int minutes=prefs.getInt("refresh_minutes",0);return RefreshPolicy.validMinutes(minutes)?minutes:0;}
    synchronized void setRefreshMinutes(int minutes) throws Exception {if(!RefreshPolicy.validMinutes(minutes))throw new IllegalArgumentException("invalid_refresh");if(!prefs.edit().putInt("refresh_minutes",minutes).commit())throw new Exception("save_failed");}
    synchronized void setRebootEnabled(boolean enabled) throws Exception {if(!prefs.edit().putBoolean("reboot_enabled",enabled).commit())throw new Exception("save_failed");}
    synchronized String token() throws Exception {
        if (!hasToken()) return "";
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(prefs.getString("token_iv", ""), Base64.NO_WRAP)));
        byte[] plain = c.doFinal(Base64.decode(prefs.getString("token_cipher", ""), Base64.NO_WRAP));
        try { return new String(plain, StandardCharsets.UTF_8); } finally { Arrays.fill(plain, (byte)0); }
    }
    synchronized void saveToken(String token) throws Exception {
        clearSnapshot();
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, key());
        byte[] bytes = token.getBytes(StandardCharsets.UTF_8);
        try { byte[] encrypted = c.doFinal(bytes); if (!prefs.edit().putString("token_cipher", b64(encrypted)).putString("token_iv", b64(c.getIV())).putString("token_binding",binding(token)).commit()) throw new Exception("save_failed"); }
        finally { Arrays.fill(bytes, (byte)0); }
    }
    synchronized void clearToken() throws Exception {
        clearSnapshot();
        if (!prefs.edit().remove("token_cipher").remove("token_iv").remove("token_binding").commit()) throw new Exception("save_failed");
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore"); ks.load(null); if (ks.containsAlias(ALIAS)) ks.deleteEntry(ALIAS);
    }
    synchronized void saveSnapshot(JSONObject snapshot, String credential) throws Exception {
        if(credential.isEmpty()||snapshot.optBoolean("cached")||snapshot.optBoolean("stale")||!snapshot.optBoolean("instances_known")||snapshot.optJSONArray("issues")==null||snapshot.getJSONArray("issues").length()>0)return;
        long measured=java.time.Instant.parse(snapshot.getString("updated_at")).toEpochMilli();
        JSONObject envelope=new JSONObject().put("version",1).put("saved_at",measured).put("binding",binding(credential)).put("data",snapshot);
        byte[] plain=envelope.toString().getBytes(StandardCharsets.UTF_8);
        try{if(plain.length>2*1024*1024)return;Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key(SNAPSHOT_ALIAS));byte[] encrypted=c.doFinal(plain);
            if(!prefs.edit().putString("snapshot_cipher",b64(encrypted)).putString("snapshot_iv",b64(c.getIV())).commit())throw new Exception("save_failed");
        }finally{Arrays.fill(plain,(byte)0);}
    }
    synchronized JSONObject snapshot(String credential) {
        if(credential.isEmpty()||!prefs.contains("snapshot_cipher"))return null;
        try{Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(SNAPSHOT_ALIAS),new GCMParameterSpec(128,Base64.decode(prefs.getString("snapshot_iv",""),Base64.NO_WRAP)));
            byte[] plain=c.doFinal(Base64.decode(prefs.getString("snapshot_cipher",""),Base64.NO_WRAP));
            try{JSONObject envelope=new JSONObject(new String(plain,StandardCharsets.UTF_8)),snapshot=envelope.getJSONObject("data");String month=java.time.YearMonth.now(java.time.ZoneOffset.UTC).toString();
                if(envelope.optInt("version")!=1||!binding(credential).equals(envelope.optString("binding"))||!RetentionPolicy.snapshotValid(envelope.optLong("saved_at"),System.currentTimeMillis(),snapshot.optString("month_utc"),month)){clearSnapshot();return null;}
                return snapshot.put("cached",true).put("stale",System.currentTimeMillis()-envelope.optLong("saved_at")>=RefreshPolicy.MIN_INTERVAL_MS);
            }finally{Arrays.fill(plain,(byte)0);}
        }catch(Exception e){try{clearSnapshot();}catch(Exception ignored){}return null;}
    }
    synchronized void clearSnapshot() throws Exception {
        if(!prefs.edit().remove("snapshot_cipher").remove("snapshot_iv").commit())throw new Exception("save_failed");
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);if(ks.containsAlias(SNAPSHOT_ALIAS))ks.deleteEntry(SNAPSHOT_ALIAS);
    }
    private static String binding(String credential) throws Exception {byte[] bytes=credential.getBytes(StandardCharsets.UTF_8);try{return b64(MessageDigest.getInstance("SHA-256").digest(bytes));}finally{Arrays.fill(bytes,(byte)0);}}
    private static String b64(byte[] b) { return Base64.encodeToString(b, Base64.NO_WRAP); }
}

