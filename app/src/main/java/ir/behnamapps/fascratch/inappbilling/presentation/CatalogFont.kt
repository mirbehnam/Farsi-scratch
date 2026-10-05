package ir.behnamapps.fascratch.inappbilling.presentation

/** Font bytes come exclusively from R.font.shabnam, never from a server/template URL. */
internal object CatalogFont {
    fun bootstrap(base64: String): String {
        require(base64.length in 1..524288 && base64.matches(Regex("[A-Za-z0-9+/]+={0,2}")))
        return """
            (function(){
              window.__scratchFontReady=false;
              try {
                const bytes=Uint8Array.from(atob('$base64'),c=>c.charCodeAt(0));
                const font=new FontFace('Shabnam',bytes.buffer,{style:'normal',weight:'400'});
                font.load().then(function(){
                  document.fonts.add(font);
                  const style=document.createElement('style');
                  style.id='scratch-bundled-font';
                  style.textContent='html,body,button,input,select,textarea{font-family:Shabnam,Tahoma,sans-serif!important}';
                  document.head.appendChild(style);
                  return document.fonts.ready;
                }).then(function(){
                  window.__scratchFontReady=true;
                  if(window.__scratchHealthy && window.__scratchHealthy())
                    window.ScratchNative.postMessage(JSON.stringify({type:'heartbeat'}));
                }).catch(function(){window.ScratchNative.postMessage(JSON.stringify({type:'error'}));});
                return true;
              }catch(e){return false;}
            })();
        """.trimIndent()
    }
}
