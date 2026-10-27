const fs=require('node:fs'),vm=require('node:vm'),path=require('node:path'),assert=require('node:assert/strict');
const source=fs.readFileSync(path.join(__dirname,'../assets/web/app.js'),'utf8').replace(/\nboot\(\);\s*$/,'\n');
let now=1000000,passed=0,sequence=0;const timers=new Map(),requests=[];
class Clock extends Date {constructor(...args){super(...(args.length?args:[now]))}static now(){return now}}
const document={visibilityState:'visible',querySelector(){return {}},addEventListener(){}};
const ctx={Date:Clock,window:{addEventListener(){}},document,navigator:{onLine:true},location:{hash:''},URLSearchParams,setTimeout(fn,delay){const id=++sequence;timers.set(id,{fn,delay});return id},clearTimeout(id){timers.delete(id)},console,requests};
vm.createContext(ctx);vm.runInContext(source,ctx);const run=c=>vm.runInContext(c,ctx),check=fn=>{fn();passed++};
run(`render=()=>scheduleRebootPolling();toast=()=>{};api=async(path,options)=>{requests.push({path,body:options?.body});if(replyError)throw replyError;return reply};state.authenticated=true;state.view='instances';state.data={instances:[{id:'one',health:'healthy',status:'active',power_status:'running',server_status:'ok'}]};`);
ctx.replyError=null;ctx.reply={ok:true,cached:false,instance:{id:'one',status:'active',power_status:'running',server_status:'ok',health:'healthy'}};
async function tick(delta){now+=delta;run("state.rebootWatches.one.nextAt=Date.now()");await run('pollRebootStatuses()')}
(async()=>{
 run("watchReboot('one')");check(()=>assert.equal([...timers.values()][0].delay,5000));
 check(()=>assert.match(run('status(data().instances[0])'),/重启中/));
 await tick(5000);check(()=>assert.equal(requests[0].path,'/api/instance-status'));check(()=>assert.deepEqual(JSON.parse(requests[0].body),{instance_id:'one'}));
 check(()=>assert.ok(run('state.rebootWatches.one')));check(()=>assert.equal([...timers.values()][0].delay,10000));
 ctx.reply.instance={...ctx.reply.instance,server_status:'installingbooting',health:'warning'};await tick(10000);
 check(()=>assert.equal(run('state.data.instances[0].server_status'),'installingbooting'));check(()=>assert.match(run('status(data().instances[0])'),/启动中/));check(()=>assert.equal([...timers.values()][0].delay,20000));
 ctx.reply.instance={...ctx.reply.instance,server_status:'ok',health:'healthy'};await tick(20000);
 check(()=>assert.equal(run('state.rebootWatches.one'),undefined));check(()=>assert.match(run('status(data().instances[0])'),/运行中/));check(()=>assert.equal(timers.size,0));
 run("watchReboot('one')");await tick(5000);await tick(10000);check(()=>assert.ok(run('state.rebootWatches.one')));
 await tick(45000);check(()=>assert.equal(run('state.rebootWatches.one'),undefined));
 run("watchReboot('one')");ctx.replyError=Object.assign(new Error('limited'),{status:429,retry:'90'});await tick(5000);
 check(()=>assert.equal([...timers.values()][0].delay,90000));check(()=>assert.equal(run('state.rebootWatches.one.failed'),true));check(()=>assert.match(run('status(data().instances[0])'),/状态待确认/));
 check(()=>assert.equal(run('state.localLogs[0].section'),'instance_status'));
 ctx.replyError=null;document.visibilityState='hidden';run('scheduleRebootPolling()');check(()=>assert.equal(timers.size,0));
 document.visibilityState='visible';run("state.view='traffic';scheduleRebootPolling()");check(()=>assert.equal(timers.size,0));
 run("state.view='instances';scheduleRebootPolling()");check(()=>assert.equal(timers.size,1));
 ctx.navigator.onLine=false;run('scheduleRebootPolling()');check(()=>assert.equal(timers.size,0));ctx.navigator.onLine=true;
 const before=requests.length;await tick(600000);check(()=>assert.equal(requests.length,before));check(()=>assert.equal(run('state.rebootWatches.one.timedOut'),true));check(()=>assert.equal(timers.size,0));
 check(()=>assert.match(run('rebootNotice(data().instances[0])'),/手动刷新/));
 run("watchReboot('one')");let finish;ctx.apiPending=new Promise(r=>finish=r);run('api=()=>apiPending');now+=5000;
 const pending=run('pollRebootStatuses()');run('state.sessionEpoch++;state.rebootWatches={};state.data=null;state.authenticated=false');finish(ctx.reply);await pending;
 check(()=>assert.equal(run('state.data'),null));check(()=>assert.equal(timers.size,0));
 // A temporary secure-storage failure gets retried, permanent expiry does not.
 let calls=0;ctx.sessionReply=()=>++calls===1?{authenticated:false,retry_login_restore:true}:{authenticated:true};
 run('api=async()=>sessionReply()');ctx.setTimeout=(fn)=>{fn();return 0};
 const recovered=await run('restoreSession()');check(()=>assert.equal(recovered.authenticated,true));check(()=>assert.equal(calls,2));
 calls=0;ctx.sessionReply=()=>{calls++;return {authenticated:false,retry_login_restore:false}};
 const expired=await run('restoreSession()');check(()=>assert.equal(expired.authenticated,false));check(()=>assert.equal(calls,1));
 console.log(`Reboot monitoring and restore retries: ${passed}/${passed} checks passed`);
})().catch(e=>{console.error(e);process.exitCode=1});
