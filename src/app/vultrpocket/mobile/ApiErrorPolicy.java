package app.vultrpocket.mobile;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ApiErrorPolicy {
    static String classify(int status, String body) {
        String text=body==null?"":body.toLowerCase(Locale.ROOT);
        if((status==401||status==403)&&(text.contains("unauthorized ip address")||text.contains("ip address is not allowed")||text.contains("ip address not allowed")))return "source_ip_denied";
        return "upstream_"+status;
    }
    static String sourceIp(String body) {
        Matcher m=Pattern.compile("(?i)unauthorized ip address:\\s*([0-9a-f:.]{3,64})").matcher(body==null?"":body);
        return m.find()?m.group(1):"";
    }
    static String message(String code) {
        if(code.equals("not_configured"))return "请先在设置中添加 Token";
        if(code.equals("reboot_result_unknown"))return "未收到重启确认，请先刷新实例状态，再决定是否重试";
        if(code.equals("reboot_cooldown"))return "刚刚已尝试重启，请至少等待一分钟";
        if(code.equals("reboot_target_unknown"))return "实例信息已过期或不可重启，请先刷新实例列表";
        if(code.equals("upstream_400"))return "操作未被接受，请刷新实例状态后重试";
        if(code.equals("upstream_404"))return "未找到实例，请刷新实例列表";
        if(code.equals("source_ip_denied"))return "来源 IP 未授权，请在 Vultr 允许手机当前网络的出口 IP";
        if(code.equals("upstream_401"))return "认证失败，请检查 Token 是否有效、已过期或已撤销";
        if(code.equals("upstream_403"))return "接口访问被拒绝，请检查 Token 权限及访问限制";
        if(code.equals("upstream_429"))return "API 请求过于频繁，请稍后重试";
        if(code.equals("timeout"))return "请求超时，请检查网络后重试";
        if(code.equals("network_or_format"))return "网络连接或响应格式异常";
        if(code.equals("missing_field"))return "接口响应缺少所需字段";
        return "数据暂不可用";
    }
}
