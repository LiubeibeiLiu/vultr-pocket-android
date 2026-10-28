package app.vultrpocket.mobile;

import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import org.json.JSONObject;

final class I18n {
    private static volatile String language="zh-CN";
    private static JSONObject english=new JSONObject();
    static void initialize(Context context,String selected)throws Exception{
        try(InputStream input=context.getAssets().open("web/locales/en.json");ByteArrayOutputStream output=new ByteArrayOutputStream()){
            byte[] buffer=new byte[4096];int count;
            while((count=input.read(buffer))!=-1){output.write(buffer,0,count);if(output.size()>128*1024)throw new Exception("locale_too_large");}
            english=new JSONObject(new String(output.toByteArray(),java.nio.charset.StandardCharsets.UTF_8));
        }
        setLanguage(selected);
    }
    static void setLanguage(String selected){language=LanguagePolicy.normalize(selected);}
    static String text(String source){return source==null?null:"en".equals(language)?english.optString(source,source):source;}
}
