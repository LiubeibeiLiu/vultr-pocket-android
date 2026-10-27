package app.vultrpocket.mobile;

import java.util.concurrent.*;

public final class BridgeDispatchTest {
    public static void main(String[] args)throws Exception {
        int passed=0;
        for(String action:new String[]{"settings","logs","clear_logs","edit_token","edit_password","clear_token","copy_ip"}){if(!BridgeDispatch.local(action,false))throw new AssertionError(action);passed++;}
        for(String action:new String[]{"dashboard","instance_status","login","logout","session","reboot_instance","preferences"}){if(BridgeDispatch.local(action,false))throw new AssertionError(action);passed++;}
        if(!BridgeDispatch.local("preferences",true))throw new AssertionError("refresh preference must not wait on network");passed++;
        if(BridgeDispatch.local("dashboard",true))throw new AssertionError("dashboard never runs in local queue");passed++;
        ExecutorService network=Executors.newSingleThreadExecutor(),local=Executors.newSingleThreadExecutor();
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1),settingsDone=new CountDownLatch(1),networkDone=new CountDownLatch(1);
        try{
            network.execute(()->{entered.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}finally{networkDone.countDown();}});
            if(!entered.await(2,TimeUnit.SECONDS))throw new AssertionError("network task did not start");
            (BridgeDispatch.local("settings",false)?local:network).execute(settingsDone::countDown);
            if(!settingsDone.await(2,TimeUnit.SECONDS))throw new AssertionError("settings blocked behind network");passed++;
            if(networkDone.getCount()!=1)throw new AssertionError("test did not keep network blocked");passed++;
        }finally{release.countDown();network.shutdownNow();local.shutdownNow();}
        System.out.println("BridgeDispatch: "+passed+"/"+passed+" checks passed");
    }
}
