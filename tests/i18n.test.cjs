const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),assert=require('node:assert/strict');
const root=path.join(__dirname,'..'),web=path.join(root,'assets/web'),messages=JSON.parse(fs.readFileSync(path.join(web,'locales/en.json'),'utf8'));
const source=fs.readFileSync(path.join(web,'app.js'),'utf8').replace(/\nboot\(\);\s*$/,'\n');
function context(){const nodes={};const document={documentElement:{lang:''},querySelector(s){return nodes[s]||(nodes[s]={innerHTML:'',addEventListener(){},setAttribute(){},classList:{remove(){}}})},querySelectorAll(){return []},addEventListener(){}};const ctx={window:{PocketMessages:messages,addEventListener(){},scrollTo(){}},document,navigator:{onLine:true},location:{hash:''},URLSearchParams,console,setTimeout(fn,ms){const timer=setTimeout(fn,ms);timer.unref();return timer},clearTimeout};vm.createContext(ctx);vm.runInContext(fs.readFileSync(path.join(web,'i18n.js'),'utf8'),ctx);vm.runInContext(source,ctx);return {ctx,nodes,run:code=>vm.runInContext(code,ctx)};}
let passed=0;const check=fn=>{fn();passed++};
(async()=>{
 const c=context();check(()=>assert.equal(c.run('state.language'),'zh-CN'));check(()=>assert.equal(c.run("tr('仪表')"),'仪表'));
 c.run("applyLanguage('en')");check(()=>assert.equal(c.ctx.document.documentElement.lang,'en'));check(()=>assert.equal(c.ctx.document.title,'Vultr Pocket · Cloud Dashboard'));check(()=>assert.equal(c.run("tr('仪表')"),'Dashboard'));
 check(()=>assert.equal(c.run("tr('not-a-key')"),'not-a-key'));check(()=>assert.equal(c.run("logCount(1)"),'1 record'));check(()=>assert.equal(c.run("logCount(2)"),'2 records'));
 check(()=>assert.equal(c.run('refreshChoiceLabel(5)'),'5 min'));check(()=>assert.equal(c.run('refreshChoiceLabel(0)'),'Off'));
 check(()=>assert.equal(c.run("sectionName('instance_bandwidth:sample')"),'Instance bandwidth'));
 const snapshot={instances_known:true,instances:[{id:'sample',label:'Sample VPS',main_ip:'192.0.2.1',v6_main_ip:'2001:db8::1',city:'Tokyo',plan:'sample-plan',os:'Linux',ram:1024,disk:25,vcpu_count:1,allowed_bandwidth:1000,monthly_cost:5,health:'healthy',status:'active',power_status:'running',server_status:'ok',traffic:{in_bytes:1e9,out_bytes:2e9,total_bytes:3e9,plan_remaining_gb_estimate:998,plan_percent_estimate:0.2,daily:[{date:'2026-10-01',in_bytes:1e9,out_bytes:2e9}]}}],currency:'USD',month_utc:'2026-10',updated_at:'2026-10-01T00:00:00Z',account:{balance:-10,pending_charges:3.75},account_bandwidth:{current_month_to_date:{gb_in:1,gb_out:2}},invoices_known:true,invoices:[],issues:[],stale:false};
 c.ctx.snapshot=snapshot;c.run("state.data=snapshot;state.settings={language:'en',token_set:false,log_count:1};state.booting=false;state.logs=[{section:'account',time:'2026-10-01T00:00:00Z',code:'upstream_403',message:'接口访问被拒绝，请检查 Token 权限及访问限制'}]");
 for(const name of ['overview','instancesView','trafficView','billingView','settingsView','logsView','logView'])check(()=>assert.doesNotMatch(c.run(name+'()'),/[\u3400-\u9fff]/,name+' contains untranslated UI'));
 check(()=>assert.match(c.run('logView()'),/API access denied/));check(()=>assert.match(c.run('settingsView()'),/data-language-picker/));check(()=>assert.match(c.run('languageCard({language:"en"})'),/English/));
 c.run('login()');check(()=>assert.doesNotMatch(c.nodes['#app'].innerHTML,/[\u3400-\u9fff]/));check(()=>assert.match(c.nodes['#app'].innerHTML,/Remember login/));
 c.run("state.data.instances[0].label='用户实例名称'");check(()=>assert.match(c.run('instancesView()'),/用户实例名称/));
 c.run("applyLanguage('zh-CN')");check(()=>assert.equal(c.run("sectionName('account')"),'账户信息'));check(()=>assert.equal(c.run('logCount(2)'),'2 条记录'));
 check(()=>assert.equal(c.run("logMessage('Request timed out. Try again.')"),'请求超时，请重试'));
 check(()=>assert.equal(c.run("logMessage('Unrecognized external diagnostic')"),'Unrecognized external diagnostic'));
 c.run("state.logs[0].message='Request timed out. Try again.'");check(()=>assert.match(c.run('logView()'),/请求超时，请重试/));
 c.run("applyLanguage('en')");check(()=>assert.match(c.run('logView()'),/Request timed out\. Try again\./));
 c.run("applyLanguage('invalid')");check(()=>assert.equal(c.run('state.language'),'zh-CN'));
 c.run("applySession({authenticated:false,language:'en',refresh_minutes:0})");check(()=>assert.equal(c.run('state.language'),'en'));
 const calls=[];c.ctx.calls=calls;c.run("render=()=>{};toast=()=>{};state.authenticated=true;api=async(route,options)=>{calls.push({route,body:JSON.parse(options.body)});return {language:'zh-CN'}}");
 await c.run("saveLanguage('zh-CN')");check(()=>assert.equal(calls.length,1));check(()=>assert.equal(calls[0].route,'/api/preferences'));check(()=>assert.deepEqual(JSON.parse(JSON.stringify(calls[0].body)),{language:'zh-CN'}));check(()=>assert.equal(c.run('state.language'),'zh-CN'));check(()=>assert.equal(c.run('state.settingsBusy'),false));
 await c.run("saveLanguage('fr')");check(()=>assert.equal(calls.length,1));
 c.run("api=async()=>{throw Error('simulated save failure')}");await c.run("saveLanguage('en')");check(()=>assert.equal(c.run('state.language'),'zh-CN'));check(()=>assert.equal(c.run('state.settingsBusy'),false));
 // Every native Chinese message and frontend literal has an English catalog entry.
 const han=/[\u3400-\u9fff]/,missing=[];
 for(const name of fs.readdirSync(path.join(root,'src/app/vultrpocket/mobile')).filter(n=>n.endsWith('.java'))){const java=fs.readFileSync(path.join(root,'src/app/vultrpocket/mobile',name),'utf8');for(const match of java.matchAll(/"(?:\\.|[^"\\])*"/g)){if(!han.test(match[0]))continue;const key=JSON.parse(match[0]);if(!Object.hasOwn(messages,key))missing.push({file:name,key});}}
 check(()=>assert.deepEqual(missing,[]));
 for(const [key,value] of Object.entries(messages)){check(()=>assert.equal(typeof value,'string'));check(()=>assert.ok(value.trim()));check(()=>assert.deepEqual([...key.matchAll(/\{\w+\}/g)].map(m=>m[0]),[...value.matchAll(/\{\w+\}/g)].map(m=>m[0])));}
 const native=fs.readFileSync(path.join(root,'src/app/vultrpocket/mobile/MainActivity.java'),'utf8');check(()=>assert.match(native,/LanguagePolicy\.supported/));check(()=>assert.match(native,/vault\.setLanguage/));check(()=>assert.match(native,/\.put\("language",vault\.language\(\)\)/));
 const vault=fs.readFileSync(path.join(root,'src/app/vultrpocket/mobile/Vault.java'),'utf8');check(()=>assert.match(vault,/getString\("language","zh-CN"\)/));check(()=>assert.match(vault,/putString\("language",language\)\.commit/));
 const generated=fs.readFileSync(path.join(web,'i18n-data.js'),'utf8');check(()=>assert.ok(generated.includes(JSON.stringify(messages))));
 console.log(`Internationalization: ${passed}/${passed} checks passed`);
})().catch(e=>{console.error(e);process.exitCode=1});
