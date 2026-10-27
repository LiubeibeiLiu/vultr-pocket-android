package app.vultrpocket.mobile;

public final class RebootPolicyTest {
    private static int passed;
    private static void check(boolean result,String label){if(!result)throw new AssertionError(label);passed++;}
    public static void main(String[] args){
        check(RebootPolicy.instanceId("7cbfb016-02e7-4e5b-a5e6-c297f41296dc"),"instance id");
        for(String id:new String[]{"","../account","id/reinstall","id?force=1","id%2Freboot","https://evil.example","id\nheader",new String(new char[65]).replace('\0','a')})check(!RebootPolicy.instanceId(id),"invalid id");
        check(!RebootPolicy.instanceId(null),"null id");
        check(RebootPolicy.path("abc-123").equals("/instances/abc-123/reboot"),"single instance path");
        check(RebootPolicy.accepted(204),"204 accepted");for(int code:new int[]{200,202,301,400,401,403,404,429,500})check(!RebootPolicy.accepted(code),"not accepted");
        check(RebootPolicy.targetReady("active","running"),"running target");check(!RebootPolicy.targetReady("active","stopped"),"stopped target");check(!RebootPolicy.targetReady("pending","running"),"pending target");check(!RebootPolicy.targetReady(null,null),"unknown target");
        RebootPolicy gate=new RebootPolicy();check(gate.acquire("abc",0),"first attempt");check(!gate.acquire("abc",59999),"duplicate suppressed");check(gate.acquire("abc",60000),"cooldown elapsed");check(!gate.acquire("abc",1),"clock rollback");check(gate.acquire("other",1),"per instance");check(!gate.acquire("bad/id",1),"bad id gate");
        RebootPolicy bounded=new RebootPolicy();for(int n=0;n<128;n++)check(bounded.acquire("id-"+n,0),"bounded entries");check(!bounded.acquire("overflow",0),"capacity gate");check(bounded.acquire("overflow",60000),"expired entries evicted");
        check(RebootPolicy.stateLabel("active","running","ok").equals("运行中"),"running state label");
        check(RebootPolicy.stateLabel("active","running","installingbooting").equals("启动中"),"booting state label");
        check(RebootPolicy.stateLabel("active","running","locked").equals("处理中"),"locked state label");
        check(RebootPolicy.stateLabel("active","stopped","ok").equals("已停止"),"stopped state label");
        check(RebootPolicy.stateLabel("suspended","running","ok").equals("已暂停"),"suspended state label");
        check(RebootPolicy.stateLabel("resizing","running","ok").equals("调整配置中"),"resizing state label");
        check(RebootPolicy.stateNotice("active","running","ok").equals("重启期间服务会中断"),"running interruption notice");
        check(RebootPolicy.stateNotice("pending","stopped","none").equals("重启期间，请耐心等待"),"not running notice");
        System.out.println("RebootPolicy: "+passed+"/"+passed+" assertions passed");
    }
}
