'use strict';
const fs=require('node:fs'),path=require('node:path');
function syncLocales(){
  const web=path.join(__dirname,'../assets/web');
  const messages=JSON.parse(fs.readFileSync(path.join(web,'locales/en.json'),'utf8'));
  for(const [key,value] of Object.entries(messages))if(!key||typeof value!=='string'||!value.trim())throw Error('Invalid translation: '+key);
  const result='// Generated from locales/en.json. Do not edit.\nwindow.PocketMessages='+JSON.stringify(messages).replace(/</g,'\\u003c').replace(/\u2028/g,'\\u2028').replace(/\u2029/g,'\\u2029')+';\n';
  fs.writeFileSync(path.join(web,'i18n-data.js'),result);
  return result;
}
if(require.main===module)syncLocales();
module.exports={syncLocales};
