// Development-only synthetic preview. Never included in Android assets.
'use strict';
const http=require('node:http'),fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const base=path.join(__dirname,'../assets/web');
require('./sync-locales.cjs').syncLocales();
const publicFiles=new Set(['i18n-data.js','i18n.js','index.html','app.js','style.css','manifest.webmanifest','icon.svg','icon-192.png','icon-512.png','fonts/Tinos-Regular.ttf','fonts/Tinos-Bold.ttf']);
const mime={'.html':'text/html; charset=utf-8','.js':'text/javascript; charset=utf-8','.css':'text/css; charset=utf-8','.webmanifest':'application/manifest+json','.svg':'image/svg+xml','.png':'image/png','.ttf':'font/ttf'};
function sampleSnapshot(){
  const now=new Date(),month=now.toISOString().slice(0,7);
  const daily=Array.from({length:30},(_,n)=>({date:new Date(now.getTime()-(29-n)*86400000).toISOString().slice(0,10),in_bytes:(n%3+1)*1e9,out_bytes:(n%4+4)*1e9})).filter((_,n)=>n!==26);
  const current=daily.filter(r=>r.date.startsWith(month)),incoming=current.reduce((a,r)=>a+r.in_bytes,0),outgoing=current.reduce((a,r)=>a+r.out_bytes,0);
  const instances=['A','B'].map((v,n)=>({id:'sample-'+v,label:'Sample VPS '+v,health:'healthy',main_ip:'192.0.2.'+(n+1),v6_main_ip:'2001:db8::'+(n+1),region:'nrt',country:'JP',city:'Tokyo',plan:'sample-plan',vcpu_count:1,ram:1024,disk:25,status:'active',power_status:'running',server_status:'ok',allowed_bandwidth:1000,monthly_cost:5,os:'Linux',traffic:{in_bytes:incoming,out_bytes:outgoing,total_bytes:incoming+outgoing,plan_remaining_gb_estimate:1000-outgoing/1e9,plan_percent_estimate:outgoing/1e10,reported_days:daily.length,daily}}));
  return {currency:'USD',mode:'device',month_utc:month,updated_at:now.toISOString(),stale:false,cached:false,instances_known:true,instances,account:{balance:-10,pending_charges:3.75,last_payment_amount:10},account_bandwidth:{current_month_to_date:{gb_in:incoming*2/1e9,gb_out:outgoing*2/1e9,instance_bandwidth_credits:2000,free_bandwidth_credits:0,purchased_bandwidth_credits:0,overage:0,overage_cost:0,overage_unit_cost:0.01}},invoices_known:true,invoices:[{id:'sample-001',date:month+'-01',amount:5,balance:-10,description:'Sample monthly invoice'}],issues:[]};
}
function createPreview(){
  const sessions=new Map(),salt=crypto.randomBytes(24);let language='zh-CN',passwordHash=null,refreshMinutes=0,rebootEnabled=false,snapshot=null,lastSuccess=0,failures=0,blockedUntil=0;
  let logs=[{time:new Date().toISOString(),section:'account',code:'sample_error',message:'接口访问被拒绝，请检查 Token 权限及访问限制'},{time:new Date().toISOString(),section:'instances',code:'sample_timeout',message:'请求超时，请重试'}];
  const metadata=()=>({last_refresh_success_at:lastSuccess?new Date(lastSuccess).toISOString():null,refresh_after_ms:Math.max(0,lastSuccess+60000-Date.now())});
  const settings=()=>({ok:true,language,version:'1.1.0',compiled_at:'Browser preview',build_type:'Synthetic preview',token_set:false,remember_login:true,device_unlock_enabled:false,device_unlock_available:false,reboot_enabled:rebootEnabled,refresh_minutes:refreshMinutes,log_count:logs.length,...metadata()});
  const hash=value=>crypto.pbkdf2Sync(value,salt,210000,32,'sha256');
  const server=http.createServer(async(req,res)=>{
    const reply=(status,body)=>{res.writeHead(status,{'Content-Type':'application/json; charset=utf-8'});res.end(JSON.stringify(body));};
    res.setHeader('Cache-Control','no-store');res.setHeader('X-Content-Type-Options','nosniff');res.setHeader('Referrer-Policy','no-referrer');
    const origin='http://'+req.headers.host;
    if(!/^127\.0\.0\.1:\d+$/.test(req.headers.host||'')){reply(403,{error:'Loopback host required'});return;}
    const url=new URL(req.url,origin);
    if(url.pathname.startsWith('/api/')){
      if(req.headers.origin&&req.headers.origin!==origin){reply(403,{error:'Cross-origin requests rejected'});return;}
      if(req.headers['sec-fetch-site']==='cross-site'){reply(403,{error:'Cross-site requests rejected'});return;}
      if(!['GET','POST'].includes(req.method)){reply(405,{error:'Method not allowed'});return;}
      const cookie=(req.headers.cookie||'').match(/(?:^|; )vp_preview=([a-f0-9]{64})(?:;|$)/);let sid=cookie?.[1],session=sessions.get(sid);
      if(!session){if(sessions.size>=128)sessions.delete(sessions.keys().next().value);sid=crypto.randomBytes(32).toString('hex');session={authenticated:false};sessions.set(sid,session);res.setHeader('Set-Cookie',`vp_preview=${sid}; HttpOnly; SameSite=Strict; Path=/`);}
      let input={};
      if(req.method==='POST')try{let raw='';for await(const chunk of req){raw+=chunk;if(Buffer.byteLength(raw)>4096){reply(413,{error:'Request too large'});return;}}input=raw?JSON.parse(raw):{};if(!input||typeof input!=='object'||Array.isArray(input))throw Error();}catch{reply(400,{error:'Invalid JSON'});return;}
      const route=url.pathname;
      if(route==='/api/session'&&req.method==='GET'){reply(200,{authenticated:session.authenticated,password_setup_required:passwordHash===null,language,version:'1.1.0',remember_login:true,device_unlock_enabled:false,device_unlock_available:false,reboot_enabled:session.authenticated&&rebootEnabled,refresh_minutes:refreshMinutes,snapshot:session.authenticated?snapshot:null,...metadata()});return;}
      if(['/api/setup-password','/api/login'].includes(route)&&req.method==='POST'){
        if(Date.now()<blockedUntil){res.setHeader('Retry-After','30');reply(429,{error:'稍后再试'});return;}
        if(route==='/api/setup-password'){
          if(passwordHash!==null){reply(409,{error:'访问密码已设置'});return;}
          if(typeof input.password!=='string'||input.password.length<8||input.password.length>512||input.password!==input.confirmation){reply(400,{error:'密码须为8至512位，且两次输入相同'});return;}
          passwordHash=hash(input.password);
        }else if(!passwordHash||typeof input.password!=='string'||input.password.length>512||!crypto.timingSafeEqual(hash(input.password),passwordHash)){
          if(++failures>=5){blockedUntil=Date.now()+30000;failures=0;}reply(401,{error:'访问密码不正确'});return;
        }
        session.authenticated=true;failures=0;reply(200,{ok:true});return;
      }
      if(!session.authenticated){reply(401,{error:'请先登录'});return;}
      if(route==='/api/logout'&&req.method==='POST'){session.authenticated=false;reply(200,{ok:true});return;}
      if(route==='/api/dashboard'&&req.method==='GET'){
        if(url.searchParams.get('refresh')==='1'||Date.now()-lastSuccess>=60000){snapshot=sampleSnapshot();lastSuccess=Date.now();}
        reply(200,{...(snapshot||sampleSnapshot()),...metadata()});return;
      }
      if(route==='/api/settings'&&req.method==='POST'){reply(200,settings());return;}
      if(route==='/api/preferences'&&req.method==='POST'){
        if(input.language!==undefined){if(!['zh-CN','en'].includes(input.language)){reply(400,{error:'Invalid language'});return;}language=input.language;}
        if(input.refresh_minutes!==undefined){if(![0,1,3,5].includes(input.refresh_minutes)){reply(400,{error:'Invalid refresh interval'});return;}refreshMinutes=input.refresh_minutes;}
        if(input.reboot_enabled!==undefined)rebootEnabled=input.reboot_enabled===true;
        reply(200,settings());return;
      }
      if(route==='/api/logs'&&req.method==='POST'){reply(200,{entries:logs});return;}
      if(route==='/api/clear-logs'&&req.method==='POST'){logs=[];reply(200,{ok:true});return;}
      if(route==='/api/reboot-instance'&&req.method==='POST'){reply(200,{cancelled:true});return;}
      if(['/api/edit-token','/api/clear-token','/api/edit-password','/api/device-auth'].includes(route)){reply(409,{error:'此操作请在 Android APP 中使用；预览不接收真实凭据'});return;}
      reply(404,{error:'Unsupported preview operation'});return;
    }
    if(req.method!=='GET'){reply(405,{error:'Method not allowed'});return;}
    const name=url.pathname==='/'?'index.html':url.pathname.slice(1);
    if(!publicFiles.has(name)){reply(404,{error:'Not found'});return;}
    res.setHeader('Content-Security-Policy',"default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self' data:; font-src 'self'; connect-src 'self'; manifest-src 'self'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'");
    try{const body=fs.readFileSync(path.join(base,name));res.writeHead(200,{'Content-Type':mime[path.extname(name)]||'application/octet-stream'});res.end(body);}catch{reply(404,{error:'Not found'});}
  });
  return server;
}
if(require.main===module){const port=process.env.PORT===undefined?8088:Number(process.env.PORT);if(!Number.isInteger(port)||port<1||port>65535)throw Error('Invalid PORT');const server=createPreview();server.listen(port,'127.0.0.1',()=>console.log(`Synthetic UI preview: http://127.0.0.1:${port} (no Vultr requests)`));}
module.exports={createPreview,sampleSnapshot};
