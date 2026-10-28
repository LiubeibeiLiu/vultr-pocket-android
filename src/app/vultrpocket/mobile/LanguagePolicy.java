package app.vultrpocket.mobile;

final class LanguagePolicy {
    static boolean supported(String value){return "zh-CN".equals(value)||"en".equals(value);}
    static String normalize(String value){return supported(value)?value:"zh-CN";}
}
