package app.vultrpocket.mobile;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

public final class LoginTicketStoreTest {
    static int passed;static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);passed++;}
    static final class Disk implements LoginTicketStore.Backend {
        final Map<String,String> values=new HashMap<>();long seen;SecretKey key;boolean unavailable,writeFailure;
        Disk()throws Exception{KeyGenerator g=KeyGenerator.getInstance("AES");g.init(256);key=g.generateKey();}
        public String read(String name){return values.getOrDefault(name,"");}public long seen(){return seen;}
        public void save(String cipher,String iv,long time)throws Exception{if(writeFailure)throw new Exception("disk");values.put("login_cipher",cipher);values.put("login_iv",iv);seen=time;}
        public void seen(long time)throws Exception{if(writeFailure)throw new Exception("disk");seen=time;}
        public SecretKey key()throws Exception{if(unavailable)throw new Exception("temporarily_locked");return key;}
    }
    public static void main(String[] args)throws Exception {
        long start=1000000;Disk disk=new Disk();LoginTicketStore first=new LoginTicketStore(disk,null);
        check(!first.restore("binding",start),"first install requires login");
        first.issue("binding",start);check(first.valid(start),"new ticket active");
        String saved=disk.read("login_cipher");check(!new String(Base64.getDecoder().decode(saved),StandardCharsets.UTF_8).contains("binding"),"binding encrypted");
        // Recreate the Activity-side store; only persisted bytes and key survive.
        LoginTicketStore reopened=new LoginTicketStore(disk,null);
        check(reopened.restore("binding",start+1000),"cold start restores encrypted ticket");
        check(reopened.valid(start+8*60*60*1000L),"activity session expiry does not expire ticket");
        check(new LoginTicketStore(disk,null).restore("binding",start+29*86400000L),"process recreation after 29 days");
        check(disk.read("login_cipher").equals(saved),"restore never renews lifetime");
        check(!new LoginTicketStore(disk,null).restore("binding",start+RetentionPolicy.LOGIN_TTL),"30 day fixed expiry");
        check(disk.read("login_cipher").equals(saved),"invalid read does not destroy stored bytes");
        first.issue("binding",start);disk.unavailable=true;
        LoginTicketStore retry=new LoginTicketStore(disk,null);check(!retry.restore("binding",start+1000),"locked secure storage fails closed");
        check(retry.failure().equals("secure_storage_unavailable"),"transient failure can retry");
        disk.unavailable=false;check(retry.restore("binding",start+1000),"same ticket recovers without password after transient error");
        check(!new LoginTicketStore(disk,null).restore("different_password",start+2000),"password binding checked");
        check(new LoginTicketStore(disk,null).restore("binding",start+500),"small phone clock correction allowed");
        check(disk.seen==start+1000,"clock high water never decreases");
        check(!new LoginTicketStore(disk,null).restore("binding",start-300001),"large clock rollback rejected");
        disk.writeFailure=true;check(!new LoginTicketStore(disk,null).restore("binding",start+3000),"failed persistence fails closed");
        disk.writeFailure=false;check(new LoginTicketStore(disk,null).restore("binding",start+3000),"write failure preserves recoverable ticket");
        disk.values.put("login_cipher",saved.substring(1));check(!new LoginTicketStore(disk,null).restore("binding",start+4000),"tampering rejected");
        first.issue("binding",start);first.forget();check(!first.valid(start),"memory cleared on logout");
        disk.values.clear();check(!new LoginTicketStore(disk,null).restore("binding",start),"logout removes persisted login");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,disk.key);
        disk.save(Base64.getEncoder().encodeToString(cipher.doFinal("{legacy-v2}".getBytes(StandardCharsets.UTF_8))),Base64.getEncoder().encodeToString(cipher.getIV()),start);
        LoginTicketStore upgraded=new LoginTicketStore(disk,plain->new LoginTicketStore.Claims("binding",start,start+RetentionPolicy.LOGIN_TTL));
        check(upgraded.restore("binding",start+1),"existing v2 ciphertext migration callback");
        check(!upgraded.restore("binding",start+RetentionPolicy.LOGIN_TTL),"legacy lifetime not extended");
        System.out.println("LoginTicketStore: "+passed+"/"+passed+" checks passed");
    }
}
