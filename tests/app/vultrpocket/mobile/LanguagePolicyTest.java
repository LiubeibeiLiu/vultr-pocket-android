package app.vultrpocket.mobile;

public final class LanguagePolicyTest {
    public static void main(String[] args){
        if(!LanguagePolicy.supported("zh-CN"))throw new AssertionError("Chinese rejected");
        if(!LanguagePolicy.supported("en"))throw new AssertionError("English rejected");
        if(LanguagePolicy.supported(null))throw new AssertionError("null accepted");
        if(LanguagePolicy.supported("en-US"))throw new AssertionError("unsupported alias accepted");
        if(LanguagePolicy.supported("../../en"))throw new AssertionError("path accepted");
        if(!LanguagePolicy.normalize(null).equals("zh-CN"))throw new AssertionError("null default");
        if(!LanguagePolicy.normalize("").equals("zh-CN"))throw new AssertionError("empty default");
        if(!LanguagePolicy.normalize("en").equals("en"))throw new AssertionError("English lost");
        if(!LanguagePolicy.normalize("unsupported").equals("zh-CN"))throw new AssertionError("unknown default");
        System.out.println("LanguagePolicy: 9/9 checks passed");
    }
}
