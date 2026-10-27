package app.vultrpocket.mobile;

final class BridgeDispatch {
    static boolean local(String action,boolean refreshPreference){
        return "settings".equals(action)||"logs".equals(action)||"clear_logs".equals(action)
            ||"edit_token".equals(action)||"edit_password".equals(action)||"clear_token".equals(action)
            ||"copy_ip".equals(action)||("preferences".equals(action)&&refreshPreference);
    }
}
