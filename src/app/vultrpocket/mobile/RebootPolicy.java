package app.vultrpocket.mobile;

import java.util.HashMap;
import java.util.Map;

final class RebootPolicy {
    static final long COOLDOWN_MS=60000L;
    private final Map<String,Long> attempts=new HashMap<>();
    static boolean instanceId(String id){return id!=null&&id.matches("[a-zA-Z0-9-]{1,64}");}
    static String path(String id){if(!instanceId(id))throw new IllegalArgumentException("invalid_instance");return "/instances/"+id+"/reboot";}
    static boolean accepted(int status){return status==204;}
    static boolean targetReady(String status,String power){return "active".equals(status)&&"running".equals(power);}
    static String stateLabel(String status,String power,String server){
        if("suspended".equals(status))return "已暂停";
        if("resizing".equals(status))return "调整配置中";
        if("pending".equals(status))return "部署中";
        if("locked".equals(server))return "处理中";
        if("installingbooting".equals(server))return "启动中";
        if("stopped".equals(power))return "已停止";
        return targetReady(status,power)&&"ok".equals(server)?"运行中":"状态待确认";
    }
    static String stateNotice(String status,String power,String server){
        return "运行中".equals(stateLabel(status,power,server))?"重启期间服务会中断":"重启期间，请耐心等待";
    }
    synchronized boolean acquire(String id,long now){
        if(!instanceId(id)||now<0)return false;
        Long last=attempts.get(id);if(last!=null&&(now<last||now-last<COOLDOWN_MS))return false;
        if(attempts.size()>=128){attempts.entrySet().removeIf(e->now>=e.getValue()&&now-e.getValue()>=COOLDOWN_MS);if(attempts.size()>=128)return false;}
        attempts.put(id,now);return true;
    }
}
