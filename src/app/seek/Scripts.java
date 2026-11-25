package app.seek;

import org.json.JSONObject;

import java.util.List;

/** The little bits of JavaScript the browser puts into pages. */
final class Scripts {

    static String q(String s) { return JSONObject.quote(s); }

    /** One style tag, kept up to date: the ad slots and whatever was hidden on this site. */
    static String curtain(boolean shield, List<String> hidden) {
        StringBuilder css = new StringBuilder();
        if (shield) css.append(Shield.SLOTS).append("{display:none!important}");
        for (String s : hidden) css.append(s).append("{display:none!important}");
        return "(function(){var id='__seek_curtain';var s=document.getElementById(id);"
            + "if(!s){s=document.createElement('style');s.id=id;(document.head||document.documentElement).appendChild(s);}"
            + "s.textContent=" + q(css.toString()) + ";})();";
    }

    /** Tap something and it goes, and stays gone on this site. */
    static final String PICK =
        "(function(){if(window.__seekPick)return;"
        + "var box=document.createElement('div');"
        + "box.style.cssText='position:fixed;z-index:2147483647;pointer-events:none;border:2px solid #e5484d;background:rgba(229,72,77,.12);border-radius:4px;transition:all .08s;display:none';"
        + "document.documentElement.appendChild(box);"
        + "function sel(el){if(!el||el.nodeType!==1)return null;var parts=[];var cur=el;"
        + " for(var d=0;cur&&cur.nodeType===1&&d<5;d++){"
        + "  if(cur.id&&!/\\d{3,}/.test(cur.id)&&document.querySelectorAll('#'+CSS.escape(cur.id)).length===1){parts.unshift('#'+CSS.escape(cur.id));break;}"
        + "  var p=cur.tagName.toLowerCase();if(p==='html'||p==='body'){parts.unshift(p);break;}"
        + "  var cls=[].slice.call(cur.classList).filter(function(c){return !/\\d{3,}|^(is|has)-|active|open|hover|focus/.test(c)}).slice(0,2);"
        + "  if(cls.length)p+='.'+cls.map(function(c){return CSS.escape(c)}).join('.');"
        + "  var par=cur.parentElement;if(par){var same=[].filter.call(par.children,function(x){return x.tagName===cur.tagName});"
        + "   if(same.length>1&&!cls.length)p+=':nth-of-type('+(same.indexOf(cur)+1)+')';}"
        + "  parts.unshift(p);var s=parts.join(' > ');try{if(document.querySelectorAll(s).length===1)return s;}catch(e){}"
        + "  cur=cur.parentElement;}"
        + " return parts.join(' > ');}"
        + "var target=null;"
        + "function show(el){target=el;if(!el){box.style.display='none';return;}var r=el.getBoundingClientRect();"
        + " box.style.display='block';box.style.left=r.left+'px';box.style.top=r.top+'px';box.style.width=r.width+'px';box.style.height=r.height+'px';}"
        + "function at(e){var t=e.touches&&e.touches[0]||e.changedTouches&&e.changedTouches[0]||e;return document.elementFromPoint(t.clientX,t.clientY);}"
        + "function stop(e){e.preventDefault();e.stopPropagation();e.stopImmediatePropagation&&e.stopImmediatePropagation();}"
        + "function down(e){stop(e);show(at(e));}"
        + "function up(e){stop(e);var el=at(e)||target;if(!el||el===document.body||el===document.documentElement)return;"
        + " var s=sel(el);if(s){var st=document.getElementById('__seek_curtain');if(!st){st=document.createElement('style');st.id='__seek_curtain';document.documentElement.appendChild(st);}"
        + " st.textContent+=s+'{display:none!important}';show(null);SeekBridge.hide(s);}}"
        + "var evs={touchstart:down,touchmove:down,touchend:up,click:stop,mousedown:stop,mouseup:stop,pointerdown:stop,pointerup:stop};"
        + "for(var k in evs)window.addEventListener(k,evs[k],{capture:true,passive:false});"
        + "window.__seekPick=function(){for(var k in evs)window.removeEventListener(k,evs[k],{capture:true});box.remove();window.__seekPick=null;};"
        + "})();";

    static final String UNPICK = "window.__seekPick&&window.__seekPick();";

    /** The page stripped down to the article, set in a quiet column. */
    static String reader(boolean black) { return READER.replace("#1c1c1c", black ? "#000" : "#1c1c1c"); }

    private static final String READER =
        "(function(){"
        + "function score(el){var t=0;[].forEach.call(el.querySelectorAll('p'),function(p){var l=p.innerText.trim().length;if(l>40)t+=l;});return t;}"
        + "var cands=[].slice.call(document.querySelectorAll('article,[role=main],main,[itemprop=articleBody],.post-content,.entry-content,.article-body,#content,.content'));"
        + "var best=null,bs=0;cands.forEach(function(c){var s=score(c);if(s>bs){bs=s;best=c;}});"
        + "if(!best||bs<500){var ps=document.querySelectorAll('p'),m=new Map();[].forEach.call(ps,function(p){var par=p.parentElement;if(!par)return;var l=p.innerText.trim().length;if(l>40)m.set(par,(m.get(par)||0)+l);});"
        + " m.forEach(function(v,k){if(v>bs){bs=v;best=k;}});}"
        + "if(!best||bs<200){SeekBridge.reader(false);return;}"
        + "var c=best.cloneNode(true);"
        + "c.querySelectorAll('script,style,noscript,iframe,form,button,input,select,textarea,nav,aside,footer,header,svg,[aria-hidden=true],[role=complementary],[class*=share],[class*=social],[class*=related],[class*=newsletter],[class*=promo],[class*=advert],[id*=ad-],[class*=comment]').forEach(function(x){x.remove();});"
        + "c.querySelectorAll('*').forEach(function(x){x.removeAttribute('style');x.removeAttribute('class');x.removeAttribute('width');x.removeAttribute('height');});"
        + "c.querySelectorAll('img').forEach(function(i){var s=i.getAttribute('data-src')||i.getAttribute('data-lazy-src');if(s)i.src=s;i.removeAttribute('srcset');i.removeAttribute('sizes');i.removeAttribute('loading');});"
        + "var h=document.querySelector('h1');var title=(h&&h.innerText.trim())||document.title;"
        + "var by=document.querySelector('meta[name=author]');var site=location.hostname.replace(/^www\\./,'');"
        + "var first=c.querySelector('h1');if(first&&first.innerText.trim()===title)first.remove();"
        + "var css='html{-webkit-text-size-adjust:100%}body{margin:0;background:#fff;color:#1d1d1d;font:19px/1.65 Georgia,\"Noto Serif\",serif}'"
        + "+'main{max-width:38em;margin:0 auto;padding:56px 24px 96px}h1.t{font:600 30px/1.25 -apple-system,\"HarmonyOS Sans\",Roboto,sans-serif;margin:0 0 8px}'"
        + "+'.m{font:14px/1.4 -apple-system,\"HarmonyOS Sans\",Roboto,sans-serif;color:#8c8c8c;margin-bottom:36px}img,video,figure{max-width:100%;height:auto;margin:1em 0}'"
        + "+'a{color:inherit;text-decoration-color:#bbb}h2,h3{font-family:-apple-system,\"HarmonyOS Sans\",Roboto,sans-serif;line-height:1.3;margin-top:1.8em}'"
        + "+'pre,code{font-size:15px;white-space:pre-wrap}blockquote{margin:1em 0;padding-left:1em;border-left:2px solid #ddd;color:#555}figcaption{font-size:14px;color:#8c8c8c}'"
        + "+'@media(prefers-color-scheme:dark){body{background:#1c1c1c;color:#e4e4e4}blockquote{border-color:#444;color:#aaa}a{text-decoration-color:#666}}';"
        + "document.documentElement.innerHTML='<head><meta name=viewport content=\"width=device-width,initial-scale=1\"><style>'+css+'</style></head><body><main><h1 class=t></h1><div class=m></div></main></body>';"
        + "document.querySelector('h1.t').textContent=title;document.querySelector('.m').textContent=site+(by&&by.content?' \\u00b7 '+by.content:'');"
        + "document.querySelector('main').appendChild(c);window.scrollTo(0,0);SeekBridge.reader(true);"
        + "})();";

    /** The playing video, or the biggest, made to fill the window while it floats. */
    static final String FLOAT =
        "(function(){var vs=[].slice.call(document.querySelectorAll('video'));if(!vs.length)return false;"
        + "var v=vs.filter(function(x){return !x.paused})[0]||vs.sort(function(a,b){return b.clientWidth*b.clientHeight-a.clientWidth*a.clientHeight})[0];"
        + "v.setAttribute('data-seek-float','');var s=document.getElementById('__seek_float')||document.createElement('style');s.id='__seek_float';"
        + "s.textContent='html,body{overflow:hidden!important}video[data-seek-float]{position:fixed!important;left:0!important;top:0!important;width:100vw!important;height:100vh!important;"
        + "max-width:none!important;max-height:none!important;z-index:2147483647!important;background:#000!important;object-fit:contain!important;transform:none!important}';"
        + "document.documentElement.appendChild(s);if(v.paused)v.play().catch(function(){});return true;})();";

    static final String UNFLOAT =
        "(function(){var s=document.getElementById('__seek_float');s&&s.remove();"
        + "[].forEach.call(document.querySelectorAll('[data-seek-float]'),function(v){v.removeAttribute('data-seek-float')});})();";

    static final String HAS_VIDEO = "(function(){return [].some.call(document.querySelectorAll('video'),function(v){return !v.paused&&!v.ended})})();";

    /** For a bright page in dark mode: turned around, so white becomes pure black; pictures and video turned back. */
    static final String DARKEN =
        "(function(){if(document.getElementById('__seek_dark'))return;"
        + "function lum(c){var m=(c||'').match(/[\\d.]+/g);if(!m)return -1;if(m.length>3&&+m[3]===0)return -1;return (0.2126*m[0]+0.7152*m[1]+0.0722*m[2])/255;}"
        + "var b=document.body?lum(getComputedStyle(document.body).backgroundColor):-1;if(b<0)b=lum(getComputedStyle(document.documentElement).backgroundColor);if(b<0)b=1;"
        + "if(b<0.5)return;var s=document.createElement('style');s.id='__seek_dark';"
        + "s.textContent='html{filter:invert(1) hue-rotate(180deg)!important;background:#fff!important}"
        + "img,video,picture,canvas,iframe,embed,object,[style*=\"background-image\"]{filter:invert(1) hue-rotate(180deg)!important}';"
        + "document.documentElement.appendChild(s);})();";

    private Scripts() {}
}
