package app.vultrpocket.mobile;

final class PasswordPolicy {
    static boolean needsSetup(String hash,String salt){return "".equals(hash)&&"".equals(salt);}
    static boolean acceptable(String password, String confirmation) { return password.length() >= 8 && password.length() <= 512 && password.equals(confirmation); }
}
