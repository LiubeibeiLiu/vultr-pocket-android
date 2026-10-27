const assert=require('node:assert/strict');
const {createPreview,sampleSnapshot}=require('../tools/preview.cjs');
(async()=>{
 const server=createPreview();await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));const base=`http://127.0.0.1:${server.address().port}`;let cookie='',passed=0;
 const request=async(route,body,headers={})=>{const res=await fetch(base+route,{method:body===undefined?'GET':'POST',headers:{Cookie:cookie,...(body===undefined?{}:{'Content-Type':'application/json'}),...headers},body:body===undefined?undefined:JSON.stringify(body)});if(res.headers.has('set-cookie'))cookie=res.headers.get('set-cookie').split(';')[0];return {status:res.status,data:await res.json()};};
 try{
  assert.equal((await request('/api/session')).data.password_setup_required,true);passed++;
  assert.equal((await request('/api/dashboard')).status,401);passed++;
  assert.equal((await request('/api/setup-password',{password:'short',confirmation:'short'})).status,400);passed++;
  assert.equal((await request('/api/setup-password',{password:'preview-test-input',confirmation:'preview-test-input'})).status,200);passed++;
  assert.equal((await request('/api/setup-password',{password:'replacement-input',confirmation:'replacement-input'})).status,409);passed++;
  assert.equal((await request('/api/dashboard')).data.instances[0].main_ip,'192.0.2.1');passed++;
  assert.equal((await request('/api/edit-token',{})).status,409);passed++;
  assert.equal((await request('/api/preferences',{refresh_minutes:2})).status,400);passed++;
  assert.equal((await request('/api/preferences',{refresh_minutes:5})).data.refresh_minutes,5);passed++;
  assert.equal((await request('/api/preferences',{}, {Origin:'https://example.org'})).status,403);passed++;
  assert.equal((await request('/api/logout',{})).status,200);passed++;
  assert.equal((await request('/api/dashboard')).status,401);passed++;
  assert.equal((await request('/api/login',{password:'wrong-test-input'})).status,401);passed++;
  assert.equal((await request('/api/login',{password:'preview-test-input'})).status,200);passed++;
  assert.equal((await fetch(base+'/../SECURITY.md')).status,404);passed++;
  assert.equal((await fetch(base+'/')).status,200);passed++;
  assert.equal(sampleSnapshot().instances.every(i=>i.main_ip.startsWith('192.0.2.')),true);passed++;
  assert.equal((await request('/api/reboot-instance',{instance_id:'sample-A'})).data.cancelled,true);passed++;
  console.log(`Synthetic preview: ${passed}/${passed} checks passed`);
 }finally{await new Promise(resolve=>server.close(resolve));}
})().catch(e=>{console.error(e);process.exitCode=1});
