package app.vultrpocket.mobile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Encrypted fixed-lifetime login, independent of Activity and WebView lifetimes. */
final class LoginTicketStore {
    interface Backend {
        String read(String name);
        long seen();
        void save(String cipher,String iv,long seen) throws Exception;
        void seen(long value) throws Exception;
        SecretKey key() throws Exception;
    }
    interface Legacy { Claims decode(byte[] plain) throws Exception; }
    static final class Claims {
        final String binding;final long issued,expires;
        Claims(String binding,long issued,long expires){this.binding=binding;this.issued=issued;this.expires=expires;}
    }
    private final Backend backend;private final Legacy legacy;
    private Claims active;private String failure="";
    LoginTicketStore(Backend backend,Legacy legacy){this.backend=backend;this.legacy=legacy;}
    void issue(String binding,long now) throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(DataOutputStream out=new DataOutputStream(bytes)){out.writeInt(3);out.writeUTF(binding);out.writeLong(now);out.writeLong(now+RetentionPolicy.LOGIN_TTL);}
        byte[] plain=bytes.toByteArray();
        try{Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,backend.key());
            backend.save(Base64.getEncoder().encodeToString(cipher.doFinal(plain)),Base64.getEncoder().encodeToString(cipher.getIV()),now);
            active=new Claims(binding,now,now+RetentionPolicy.LOGIN_TTL);failure="";
        }finally{Arrays.fill(plain,(byte)0);}
    }
    boolean restore(String binding,long now){
        active=null;
        try{
            String saved=backend.read("login_cipher");if(saved.isEmpty()){failure="missing";return false;}
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,backend.key(),new GCMParameterSpec(128,Base64.getDecoder().decode(backend.read("login_iv"))));
            byte[] plain=cipher.doFinal(Base64.getDecoder().decode(saved));
            try{
                Claims claims;
                if(plain.length>0&&plain[0]=='{'&&legacy!=null)claims=legacy.decode(plain);
                else try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(plain))){if(in.readInt()!=3)throw new IOException("version");claims=new Claims(in.readUTF(),in.readLong(),in.readLong());if(in.available()!=0)throw new IOException("trailing_data");}
                if(!MessageDigest.isEqual(claims.binding.getBytes(StandardCharsets.UTF_8),binding.getBytes(StandardCharsets.UTF_8))){failure="password_changed";return false;}
                if(!RetentionPolicy.loginValid(claims.issued,claims.expires,now,backend.seen())){failure="expired_or_clock";return false;}
                backend.seen(Math.max(now,backend.seen()));active=claims;failure="";return true;
            }finally{Arrays.fill(plain,(byte)0);}
        }catch(Exception e){failure="secure_storage_unavailable";return false;}
        // Failed reads never delete a valid ciphertext or its Keystore key.
    }
    boolean valid(long now){return active!=null&&RetentionPolicy.loginValid(active.issued,active.expires,now,backend.seen());}
    String failure(){return failure;}
    void forget(){active=null;}
}
