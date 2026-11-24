/* Trusted application bridge. Publisher scripts are removed and blocked by CSP. */
(() => {
  'use strict';
  let cfg, mapped = [], nodes = [], starts = [], flat = '', active = false, restoring = true;
  let selectionTimer, scrollTimer, touch;
  let originalBody;
  let displayedAnchor;
  let generation = 0;
  let requestedPage = null;
  let layoutWidth = 0, layoutHeight = 0;
  const clean = text => text.replace(/[\s\u00a0\u200b\u2060\ufeff†]/gu, '');
  const send = value => {
    if (active && cfg && window.KixyuReaderBridge) {
      window.KixyuReaderBridge.postMessage(JSON.stringify({...value, token: cfg.token,
        navigationVersion:cfg.navigationVersion,layoutVersion:cfg.layoutVersion}));
    }
  };
  const clamp = (value, min, max) => Math.max(min, Math.min(max, value));
  const reversed = () => getComputedStyle(document.documentElement).direction === 'rtl'
    || getComputedStyle(document.documentElement).writingMode === 'vertical-rl';
  const offset = () => Math.abs(window.scrollX || document.documentElement.scrollLeft || document.body.scrollLeft || 0);
  const pageScroll = (left, smooth = false) => scrollTo({left:left*(reversed()?-1:1),top:0,behavior:smooth?'smooth':'instant'});
  const isPaged = () => cfg.paged && !cfg.fixed;
  const horizontalFlow = () => isPaged() || !cfg.fixed && getComputedStyle(document.body).writingMode.startsWith('vertical');
  const maxScroll = () => Math.max(0, Math.max(document.body.scrollWidth, document.documentElement.scrollWidth) - innerWidth);
  function indexText(source = false) {
    const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
    nodes = []; starts = []; flat = '';
    let node;
    while ((node = walker.nextNode())) {
      if (node.parentElement.closest('script,style,noscript,rp')) continue;
      starts.push(flat.length); nodes.push(node); flat += clean(node.data);
    }
    let cursor = 0;
    mapped = cfg.paragraphs.map(p => {
      const text = clean(source ? (p.sourceText || p.text) : p.text), start = text ? flat.indexOf(text, cursor) : -1;
      if (start >= 0) cursor = start + text.length;
      return {...p, start, length: text.length};
    }).filter(p => p.start >= 0);
  }
  function applyCorrections() {
    indexText(true);
    mapped.filter(p => clean(p.sourceText || p.text) !== clean(p.text)).reverse().forEach(p => {
      const r = range(p.start, p.start + p.length);
      if (r) { r.deleteContents(); r.insertNode(document.createTextNode(p.text)); }
    });
    indexText();
  }
  function domPosition(global, end = false) {
    let low = 0, high = starts.length - 1, found = 0;
    while (low <= high) {
      const mid = (low + high) >> 1;
      if (starts[mid] < global || (!end && starts[mid] === global)) { found = mid; low = mid + 1; } else high = mid - 1;
    }
    const node = nodes[found];
    if (!node) return null;
    let remaining = Math.max(0, global - starts[found]);
    for (let i = 0; i < node.data.length; i++) {
      if (!clean(node.data[i])) continue;
      if (remaining === 0) return {node, offset: i};
      remaining--;
    }
    return {node, offset: node.data.length};
  }
  function sourceOffset(text, normalized, end = false) {
    let count = 0, previousEnd = 0;
    for (let i = 0; i < text.length; i++) {
      if (!clean(text[i])) continue;
      if (count++ >= normalized) return end ? previousEnd : i;
      previousEnd=i+1;
    }
    return text.length;
  }
  function globalPosition(node, rawOffset) {
    const index = nodes.indexOf(node);
    if(index>=0)return starts[index]+clean(node.data.slice(0,rawOffset)).length;
    // Select-all and DOM ranges can end on an Element boundary, not a text node.
    if(!node||node.nodeType!==Node.ELEMENT_NODE)return -1;
    const boundary=document.createRange();boundary.setStart(node,rawOffset);boundary.collapse(true);
    for(let i=0;i<nodes.length;i++){
      const point=document.createRange();point.setStart(nodes[i],0);point.collapse(true);
      if(boundary.compareBoundaryPoints(Range.START_TO_START,point)<=0)return starts[i];
    }
    return flat.length;
  }
  function range(start, end) {
    const from = domPosition(start), to = domPosition(end, true);
    if (!from || !to) return null;
    const r = document.createRange(); r.setStart(from.node, from.offset); r.setEnd(to.node, to.offset);
    return r;
  }
  function sourceLocation(global, end = false) {
    const p = mapped.find(p => global >= p.start && (end ? global <= p.start+p.length : global < p.start+p.length));
    return p ? {p: p.index, c: sourceOffset(p.text, global - p.start, end)} : null;
  }
  function caret(x, y) {
    const r = document.caretRangeFromPoint ? document.caretRangeFromPoint(x, y) : null;
    return r ? sourceLocation(globalPosition(r.startContainer, r.startOffset)) : null;
  }
  function visibleLocation() {
    // A point in the top padding can return the beginning of an offscreen multi-column
    // paragraph. Hit-test an actual visible line rect, not an arbitrary viewport point.
    for (const p of mapped) {
      const r=range(p.start,p.start+p.length);
      if(!r)continue;
      const rect=Array.from(r.getClientRects()).find(rect=>rect.bottom>0&&rect.top<innerHeight&&rect.right>0&&rect.left<innerWidth);
      if(!rect)continue;
      const style=getComputedStyle(r.startContainer.parentElement);
      const vertical=style.writingMode.startsWith('vertical');
      const x=vertical?(rect.left+rect.right)/2:style.direction==='rtl'?rect.right-1:rect.left+1;
      const y=vertical?rect.top+1:(rect.top+rect.bottom)/2;
      const found=caret(clamp(x,1,innerWidth-1),clamp(y,1,innerHeight-1));
      if(found)return found;
      return {p:p.index,c:0};
    }
    return {p:cfg.restore.p,c:cfg.restore.c};
  }
  function anchorIsVisible(anchor) {
    if (!anchor) return false;
    const p=mapped.find(p=>p.index===anchor.p);
    if(!p||!p.length)return false;
    const offset=Math.min(p.length-1,clean(p.text.slice(0,anchor.c)).length);
    const r=range(p.start+offset,p.start+offset+1);
    return r&&Array.from(r.getClientRects()).some(rect=>rect.bottom>0&&rect.top<innerHeight&&rect.right>0&&rect.left<innerWidth);
  }
  function report() {
    if (!active || restoring || innerWidth!==layoutWidth || innerHeight!==layoutHeight) return;
    const position = anchorIsVisible(displayedAnchor)?displayedAnchor:visibleLocation(), bottom = caret(innerWidth / 2, innerHeight - 16);
    displayedAnchor=position;
    const complete = cfg.fixed || (horizontalFlow() ? offset() >= maxScroll() - 2
      : scrollY + innerHeight >= Math.max(document.body.scrollHeight, document.documentElement.scrollHeight) - 2);
    send({type: 'position', ...position, end: Math.max(position.p, bottom ? bottom.p : position.p), complete});
  }
  function scrollToSource(p, c) {
    requestedPage=null;
    displayedAnchor={p,c};
    const target = mapped.find(item => item.index === p) || mapped.find(item => item.index >= p) || mapped.at(-1);
    if (!target) return;
    if(p>target.index){if(horizontalFlow())pageScroll(maxScroll());else scrollTo(0,document.documentElement.scrollHeight);return;}
    const normalized = Math.min(Math.max(0,target.length-1),clean(target.text.slice(0, clamp(c, 0, target.text.length))).length);
    const r = range(target.start + normalized, target.start + Math.min(target.length, normalized + 1));
    const rect = r && r.getBoundingClientRect();
    if (!rect) return;
    if (horizontalFlow()) {
      if(target===mapped[0]&&c===0){pageScroll(0);return;}
      const distance=reversed()?innerWidth-rect.right:rect.left;
      const scrollTarget=offset()+distance+1;
      pageScroll(isPaged()?Math.floor(scrollTarget/innerWidth)*innerWidth:clamp(scrollTarget,0,maxScroll()));
    }
    else scrollTo(0, Math.max(0, scrollY + rect.top - 8));
  }
  function applyTheme() {
    layoutWidth=innerWidth;layoutHeight=innerHeight;
    let style = document.getElementById('kixyu-reader-style');
    if (!style) { style = document.createElement('style'); style.id = 'kixyu-reader-style'; document.head.append(style); }
    if (cfg.fixed) {
      const width = cfg.viewportWidth || document.documentElement.clientWidth || innerWidth;
      const height = cfg.viewportHeight || innerHeight;
      const scale = Math.min(innerWidth / width, innerHeight / height);
      style.textContent = `html{margin:0!important;overflow:hidden!important;background:${cfg.background}!important;}
        body{margin:0!important;width:${width}px!important;height:${height}px!important;transform-origin:0 0!important;
        transform:translate(${Math.max(0,(innerWidth-width*scale)/2)}px,${Math.max(0,(innerHeight-height*scale)/2)}px) scale(${scale})!important;}
        body:not([style*="background"]){background-color:white;}`;
    } else {
      const authored=getComputedStyle(document.body), vertical=authored.writingMode.startsWith('vertical');
      const flow=`writing-mode:${authored.writingMode}!important;direction:${authored.direction}!important;`;
      style.textContent = `html{background:${cfg.background}!important;color:${cfg.foreground}!important;}
        ${cfg.font ? `@font-face{font-family:KixyuUser;src:url('/__kixyu_font?v=${cfg.fontKey}');}body,body *:not(code):not(pre){font-family:KixyuUser!important;}` : ''}
        body{margin:0!important;padding:16px ${cfg.margin}px!important;box-sizing:border-box!important;
        font-size:${cfg.fontSize}px!important;line-height:${cfg.lineHeight}!important;letter-spacing:${cfg.letterSpacing}em!important;
        background:${cfg.background}!important;color:${cfg.foreground}!important;overflow-wrap:anywhere;}
        img,svg{max-width:100%;max-height:100vh;object-fit:contain;} a{color:${cfg.accent};}
        ${cfg.paged||vertical ? `html{height:100%;overflow:${cfg.paged?'hidden':'auto'};scrollbar-width:none;${flow}}body{height:100vh!important;${vertical?'column-count:auto!important;column-width:auto!important;width:max-content!important;min-width:100%;':`column-width:${Math.max(1,innerWidth-2*cfg.margin)}px!important;column-gap:${2*cfg.margin}px!important;column-fill:auto!important;`}}` : ''}
        ::highlight(kixyu-search){background:#f6c945;color:#161616;}
        ::highlight(kixyu-note){background:#e7be6470;text-decoration:underline;}
        ::highlight(kixyu-highlight){background:#e7be6470;}
        ::highlight(kixyu-underline){text-decoration:underline 2px ${cfg.accent};}`;
      // Only correct authored foregrounds that are unreadable against their actual backing.
      const dark = cfg.dark;
      document.querySelectorAll('p,span,div,li,h1,h2,h3,h4,h5,h6,td,th').forEach(element => {
        const color = getComputedStyle(element).color.match(/[\d.]+/g);
        if (!color) return;
        let parent = element, background;
        while (parent) {
          background = getComputedStyle(parent).backgroundColor.match(/[\d.]+/g);
          if (background && (background.length < 4 || +background[3] > .5)) break;
          parent = parent.parentElement;
        }
        const light = v => +v[0]*.2126 + +v[1]*.7152 + +v[2]*.0722;
        const bg = background ? light(background) : dark ? 0 : 255;
        if (Math.abs(light(color)-bg) < 55) element.style.setProperty('color', cfg.foreground, 'important');
      });
    }
  }
  const adaptImageContrast = () => window.kixyuImageTheme.adapt(cfg);
  function paintMarks() {
    if (!window.CSS || !CSS.highlights || typeof Highlight === 'undefined') return;
    ['search','note','highlight','underline'].forEach(type => CSS.highlights.delete('kixyu-'+type));
    const marks = {search:[],note:[],highlight:[],underline:[]};
    for (const annotation of cfg.annotations || []) {
      const p = mapped.find(p => p.index === annotation.p);
      if (!p || p.text.slice(annotation.start, annotation.end) !== annotation.text) continue;
      const start = p.start + clean(p.text.slice(0, annotation.start)).length;
      const end = p.start + clean(p.text.slice(0, annotation.end)).length;
      const r = range(start, end); if (r) marks[annotation.kind].push(r);
    }
    if (cfg.search) for (const p of mapped) {
      let offset = p.text.indexOf(cfg.search);
      while (offset >= 0) {
        const start = p.start + clean(p.text.slice(0, offset)).length;
        const r = range(start, start + clean(cfg.search).length); if(r) marks.search.push(r);
        offset = p.text.indexOf(cfg.search, offset + Math.max(1, cfg.search.length));
      }
    }
    Object.entries(marks).forEach(([type,ranges]) => CSS.highlights.set('kixyu-'+type, new Highlight(...ranges)));
  }
  function selectionChanged() {
    clearTimeout(selectionTimer);
    selectionTimer = setTimeout(() => {
      const selection = getSelection();
      if (!active || !selection || selection.isCollapsed) { send({type:'selection-clear'}); return; }
      const r = selection.getRangeAt(0), start = sourceLocation(globalPosition(r.startContainer,r.startOffset));
      const end = sourceLocation(globalPosition(r.endContainer,r.endOffset), true);
      if (!start || !end || start.p > end.p || start.p === end.p && start.c >= end.c) {send({type:'selection-clear'});return;}
      const rect = r.getBoundingClientRect();
      send({type:'selection',p:start.p,endP:end.p,start:start.c,end:end.c,x:rect.left,y:rect.top,w:rect.width,h:rect.height});
    }, 100);
  }
  window.kixyuReader = {
    async open(value) {
      const run=++generation;
      cfg = value; active = value.active; restoring = true;
      originalBody = document.body.cloneNode(true);
      applyCorrections();
      applyTheme();
      await Promise.race([document.fonts.ready, new Promise(resolve=>setTimeout(resolve,800))]);
      await Promise.all(Array.from(document.images).map(image => image.complete ? null
        : Promise.race([new Promise(resolve => {image.addEventListener('load',resolve,{once:true});image.addEventListener('error',resolve,{once:true});}),new Promise(resolve=>setTimeout(resolve,800))])));
      if(run!==generation)return;
      indexText(); paintMarks(); if(!cfg.fixed)await adaptImageContrast();
      if(run!==generation)return;
      scrollToSource(cfg.restore.p,cfg.restore.c);
      requestAnimationFrame(() => requestAnimationFrame(() => { if(run!==generation)return;restoring=false; send({type:'ready'}); report(); }));
    },
    async update(value, retainedAnchor = null) {
      if (!cfg) return;
      const run=++generation;
      const anchor = retainedAnchor || (anchorIsVisible(displayedAnchor)?displayedAnchor:visibleLocation()), version = cfg.navigationVersion;
      const changed=cfg.textVersion!==value.textVersion;
      cfg = value; active = value.active; restoring = true;
      if(changed){document.body.replaceWith(originalBody.cloneNode(true));applyCorrections();}
      applyTheme(); paintMarks();if(!cfg.fixed)await adaptImageContrast();
      await Promise.race([document.fonts.ready,new Promise(resolve=>setTimeout(resolve,800))]);
      if(run!==generation)return;
      scrollToSource(...(version === cfg.navigationVersion ? [anchor.p,anchor.c] : [cfg.restore.p,cfg.restore.c]));
      requestAnimationFrame(()=>{if(run!==generation)return;restoring=false;send({type:'ready'});report();});
    },
    turn(delta) {
      if (!cfg || !active || !getSelection().isCollapsed) return;
      displayedAnchor=null; // An actual turn, unlike reflow, must report the new leading text.
      if (cfg.fixed) {send({type:'boundary',delta});return;}
      if (isPaged()) {
        const page=(requestedPage===null?Math.round(offset()/innerWidth):requestedPage)+delta;
        if(page<0||page>Math.ceil(maxScroll()/innerWidth))send({type:'boundary',delta});
        else{requestedPage=page;pageScroll(clamp(page*innerWidth,0,maxScroll()),cfg.animate);}
      } else if(horizontalFlow()) {
        if(delta<0&&offset()<=2||delta>0&&offset()>=maxScroll()-2)send({type:'boundary',delta});
        else pageScroll(clamp(offset()+delta*innerWidth*.9,0,maxScroll()),cfg.animate);
      } else {
        const target=scrollY+delta*innerHeight*.9;
        if(target < -2 || target > Math.max(document.body.scrollHeight,document.documentElement.scrollHeight)-innerHeight+2) send({type:'boundary',delta});
        else scrollTo({top:Math.max(0,target),behavior:cfg.animate?'smooth':'instant'});
      }
    },
    clearSelection(){getSelection().removeAllRanges();},
  };
  addEventListener('scroll',()=>{
    if(!restoring)send({type:'interaction',active:true});
    clearTimeout(scrollTimer);scrollTimer=setTimeout(()=>{requestedPage=null;report();send({type:'interaction',active:false});},100);
  },{passive:true});
  addEventListener('pointerdown',()=>{send({type:'interaction',active:true});},{passive:true});
  ['pointerup','pointercancel'].forEach(type=>addEventListener(type,()=>{send({type:'interaction',active:false});},{passive:true}));
  let resizeFrame;
  addEventListener('resize',()=>{
    if(cfg)restoring=true;
    cancelAnimationFrame(resizeFrame);
    const anchor=displayedAnchor;
    resizeFrame=requestAnimationFrame(()=>{if(cfg)window.kixyuReader.update(cfg,anchor);});
  },{passive:true});
  document.addEventListener('load',event=>{
    if(event.target.tagName!=='IMG'||!cfg||restoring)return;
    const anchor=displayedAnchor;
    if(!cfg.fixed)adaptImageContrast();
    if(anchor)scrollToSource(anchor.p,anchor.c);
  },true);
  document.addEventListener('selectionchange',selectionChanged);
  addEventListener('click',event=>{
    const link=event.target.closest('a[href]');
    if(link){event.preventDefault();send({type:'link',href:link.href});return;}
    if(getSelection().isCollapsed)send({type:'tap',fraction:event.clientX/innerWidth});
  });
  addEventListener('touchstart',event=>{touch=event.touches.length===1?{x:event.touches[0].clientX,y:event.touches[0].clientY,t:Date.now()}:null;},{passive:true});
  addEventListener('touchend',event=>{
    if(!cfg||!cfg.paged&&!cfg.fixed||!touch||!event.changedTouches.length||Date.now()-touch.t>500||!getSelection().isCollapsed)return;
    const dx=event.changedTouches[0].clientX-touch.x,dy=event.changedTouches[0].clientY-touch.y;touch=null;
    if(Math.abs(dx)>60&&Math.abs(dx)>Math.abs(dy)*1.5){event.preventDefault();window.kixyuReader.turn(dx<0?1:-1);}
  },{passive:false});
})();
