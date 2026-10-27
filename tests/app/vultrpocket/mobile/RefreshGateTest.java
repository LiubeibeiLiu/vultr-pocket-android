package app.vultrpocket.mobile;

import java.io.*;
import java.nio.file.*;

public final class RefreshGateTest {
    static int passed;static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);passed++;}
    static final class Disk implements RefreshGate.Store {
        final Path path;boolean fail;
        Disk(Path path){this.path=path;}
        public RefreshGate.Record load(){if(!Files.exists(path))return null;try(DataInputStream in=new DataInputStream(Files.newInputStream(path))){return new RefreshGate.Record(in.readUTF(),in.readLong(),in.readLong(),in.readLong(),in.readLong());}catch(IOException e){throw new RuntimeException(e);}}
        public void save(RefreshGate.Record r)throws Exception{if(fail)throw new IOException("write_failed");try(DataOutputStream out=new DataOutputStream(Files.newOutputStream(path))){out.writeUTF(r.binding);out.writeLong(r.succeededAt);out.writeLong(r.wall);out.writeLong(r.elapsed);out.writeLong(r.boot);}}
    }
    public static void main(String[] args)throws Exception {
        Path directory=Files.createTempDirectory("vp-refresh-gate-"),path=directory.resolve("record");
        try{
            long now=10000000,elapsed=1000000;Disk disk=new Disk(path);RefreshGate gate=new RefreshGate(disk);
            check(RefreshPolicy.MIN_INTERVAL_MS==60000,"one minute minimum");
            check(gate.remaining("token-A-hash",now,elapsed,1)==0,"first request allowed");
            check(gate.allowed("token-A-hash",now,elapsed,1,false),"automatic allowed before first success");
            check(!Files.exists(path),"starting a request does not persist a cooldown");
            check(gate.allowed("token-A-hash",now+1000,elapsed+1000,1,false),"failed request can retry without waiting");
            gate.succeeded("token-A-hash",now,elapsed,1);
            check(disk.load().succeededAt==now,"success time persisted");
            check(!gate.allowed("token-A-hash",now,elapsed,1,false),"automatic is limited after success");
            check(gate.allowed("token-A-hash",now,elapsed,1,true),"manual bypasses automatic cooldown");
            check(gate.remaining("token-A-hash",now+1000,elapsed+1000,1)==59000,"remaining duration");
            RefreshGate recreated=new RefreshGate(new Disk(path));
            check(recreated.remaining("token-A-hash",now+10000,elapsed+10000,1)==50000,"disk reload after process recreation");
            check(recreated.allowed("token-A-hash",now+10000,elapsed+10000,1,true),"manual allowed after recreation");
            check(recreated.lastSuccessAt("token-A-hash")==now,"a failure does not move prior success time");
            check(recreated.remaining("token-A-hash",now+59999,elapsed+59999,1)==1,"last automatic blocked instant");
            check(recreated.remaining("token-A-hash",now+60000,elapsed+60000,1)==0,"exact boundary");
            recreated.succeeded("token-A-hash",now+60000,elapsed+60000,1);
            check(recreated.remaining("token-A-hash",now+86400000,elapsed+61000,1)==59000,"same boot ignores clock fast forward");
            check(recreated.remaining("token-A-hash",now-86400000,elapsed+62000,1)==58000,"same boot ignores clock rollback");
            check(new RefreshGate(new Disk(path)).remaining("token-A-hash",now+70000,100,2)==50000,"phone reboot uses persisted wall time");
            check(recreated.remaining("different-token",now+70000,100,2)==0,"new credential has independent slot");
            check(recreated.remaining("token-A-hash",now-10000,100,2)==60000,"reboot with backward clock waits conservatively");
            check(recreated.lastSuccessAt("token-A-hash")==now+60000,"clock rebase preserves actual success time");
            check(new RefreshGate(new Disk(path)).remaining("token-A-hash",now,10100,2)==50000,"clock rebase is not an indefinite lock");
            check(recreated.remaining("token-A-hash",now+60000,70100,2)==0,"rebased cooldown expires");
            disk.fail=true;boolean rejected=false;try{gate.succeeded("new-token",now,elapsed,1);}catch(IOException expected){rejected=true;}
            check(rejected,"failed write reported");
            check(gate.allowed("new-token",now,elapsed,1,true),"manual requires no durable preflight write");
            check(gate.lastSuccessAt("not-current-token")==0,"other token metadata not returned");
        }finally{Files.deleteIfExists(path);Files.deleteIfExists(directory);}
        System.out.println("RefreshGate: "+passed+"/"+passed+" checks passed");
    }
}

