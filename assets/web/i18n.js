(function(global){
  'use strict';
  const messages=global.PocketMessages||{};
  const sourceByTranslation=new Map();
  for(const [source,translation] of Object.entries(messages))if(!sourceByTranslation.has(translation))sourceByTranslation.set(translation,source);
  let language='zh-CN';
  const supported=value=>value==='zh-CN'||value==='en';
  function t(key,variables={}){
    const source=String(key??'');
    const translated=language==='en'&&Object.prototype.hasOwnProperty.call(messages,source)?messages[source]:source;
    return translated.replace(/\{([a-zA-Z][a-zA-Z0-9_]*)\}/g,(match,name)=>Object.prototype.hasOwnProperty.call(variables,name)?String(variables[name]):match);
  }
  function sourceMessage(value){const source=String(value??'');return Object.prototype.hasOwnProperty.call(messages,source)?source:sourceByTranslation.get(source)||source;}
  global.PocketI18n=Object.freeze({t,supported,sourceMessage,get language(){return language;},setLanguage(value){language=supported(value)?value:'zh-CN';return language;}});
})(window);
