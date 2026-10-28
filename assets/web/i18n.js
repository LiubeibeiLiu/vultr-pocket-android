(function(global){
  'use strict';
  const messages=global.PocketMessages||{};
  let language='zh-CN';
  const supported=value=>value==='zh-CN'||value==='en';
  function t(key,variables={}){
    const source=String(key??'');
    const translated=language==='en'&&Object.prototype.hasOwnProperty.call(messages,source)?messages[source]:source;
    return translated.replace(/\{([a-zA-Z][a-zA-Z0-9_]*)\}/g,(match,name)=>Object.prototype.hasOwnProperty.call(variables,name)?String(variables[name]):match);
  }
  global.PocketI18n=Object.freeze({t,supported,get language(){return language;},setLanguage(value){language=supported(value)?value:'zh-CN';return language;}});
})(window);
