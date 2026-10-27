package app.vultrpocket.mobile;

public final class ReadLifecycleTest {
    public static void main(String[] args){
        int passed=0;ReadLifecycle lifecycle=new ReadLifecycle();
        if(lifecycle.begin()!=-1)throw new AssertionError("background starts blocked");passed++;
        lifecycle.foreground(true);long ticket=lifecycle.begin();if(!lifecycle.valid(ticket))throw new AssertionError("foreground starts read");passed++;
        lifecycle.foreground(false);if(lifecycle.valid(ticket))throw new AssertionError("background invalidates in-flight request");passed++;
        if(lifecycle.begin()!=-1)throw new AssertionError("no new background read");passed++;
        lifecycle.foreground(true);if(lifecycle.valid(ticket))throw new AssertionError("old response remains invalid after resume");passed++;
        long current=lifecycle.begin();if(!lifecycle.valid(current))throw new AssertionError("new foreground read allowed");passed++;
        lifecycle.foreground(false);lifecycle.foreground(false);lifecycle.foreground(true);if(lifecycle.valid(current))throw new AssertionError("repeated stops cannot revive read");passed++;
        if(lifecycle.valid(-1))throw new AssertionError("invalid request ticket");passed++;
        long pending=lifecycle.begin();lifecycle.cancel();if(lifecycle.valid(pending)||!lifecycle.foreground())throw new AssertionError("cancel preserves foreground but invalidates read");passed++;
        if(!lifecycle.valid(lifecycle.begin()))throw new AssertionError("read can restart after cancellation");passed++;
        System.out.println("ReadLifecycle: "+passed+"/"+passed+" checks passed");
    }
}
