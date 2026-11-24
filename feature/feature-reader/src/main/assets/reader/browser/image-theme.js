/* Image presentation only. Thresholds come from the native ReaderImageThemePolicy. */
(() => {
  'use strict';
  let epoch=0;
  const channel=x=>(x/=255)<=.04045?x/12.92:Math.pow((x+.055)/1.055,2.4);
  const luma=rgb=>.2126*channel(rgb[0])+.7152*channel(rgb[1])+.0722*channel(rgb[2]);
  const srgb=x=>Math.max(0,Math.min(255,Math.round(255*(x<=.0031308?12.92*x:1.055*Math.pow(x,1/2.4)-.055))));
  const rgb=hex=>hex.match(/[\da-f]{2}/gi).slice(0,3).map(v=>parseInt(v,16));
  const contrast=(a,b)=>(Math.max(a,b)+.05)/(Math.min(a,b)+.05);
  function profile(image,p) {
    const canvas=document.createElement('canvas');canvas.width=128;canvas.height=128;
    const context=canvas.getContext('2d',{willReadFrequently:true});context.drawImage(image,0,0,128,128);
    const pixels=context.getImageData(0,0,128,128).data,values=[],dark=[],light=[];
    let clear=0,neutral=0;
    for(let i=0;i<pixels.length;i+=4){
      if(pixels[i+3]<16)clear++;
      if(pixels[i+3]<32)continue;
      const c=[pixels[i],pixels[i+1],pixels[i+2]],value=luma(c);values.push(value);
      if(value<.18)dark.push(value);if(value>.75)light.push(value);
      if(Math.max(...c)-Math.min(...c)<=p.NEUTRAL_TOLERANCE)neutral++;
    }
    if(clear<128*128*p.MIN_TRANSPARENCY||!values.length)return null;
    const dominant=dark.length>=light.length?dark:light;
    if(dominant.length<values.length*p.SIGNIFICANT_INK_FRACTION)return null;
    values.sort((a,b)=>a-b);
    return {ink:dominant.reduce((a,b)=>a+b,0)/dominant.length,
      single:neutral>=values.length*p.SINGLE_INK_FRACTION&&dominant.length>=values.length*p.SINGLE_INK_FRACTION&&
        values[Math.round((values.length-1)*.95)]-values[Math.round((values.length-1)*.05)]<=p.MAX_INK_SPREAD};
  }
  function inkColor(cfg,page,p){
    const foreground=rgb(cfg.foreground),original=luma(foreground);
    const target=page<.18?Math.min(1,Math.max(p.SOFT_INK_LUMINANCE,(page+.05)*p.TARGET_INK_CONTRAST-.05))
      :Math.min(original,Math.max(0,(page+.05)/p.TARGET_INK_CONTRAST-.05));
    const amount=target>original?(target-original)/Math.max(.0001,1-original):target/Math.max(.0001,original);
    return '#'+foreground.map(c=>{const value=channel(c);return srgb(target>original?value+(1-value)*amount:value*amount).toString(16).padStart(2,'0');}).join('');
  }
  function backing(ink,page,p){
    const candidates=[(ink+.05)*p.MIN_CONTRAST-.05,(ink+.05)/p.MIN_CONTRAST-.05].filter(v=>v>=0&&v<=1);
    candidates.sort((a,b)=>Math.abs(a-page)-Math.abs(b-page));
    return candidates.length?'#'+srgb(candidates[0]).toString(16).padStart(2,'0').repeat(3):null;
  }
  function ownFilter(cfg,color){
    const id='kixyu-image-ink-'+cfg.token;
    let filter=document.getElementById(id);
    if(!filter){
      const ns='http://www.w3.org/2000/svg',svg=document.createElementNS(ns,'svg');
      svg.dataset.kixyuDecoration='true';svg.setAttribute('width','0');svg.setAttribute('height','0');
      svg.style.cssText='position:absolute;width:0;height:0;pointer-events:none';
      filter=document.createElementNS(ns,'filter');filter.id=id;filter.setAttribute('color-interpolation-filters','sRGB');
      filter.append(document.createElementNS(ns,'feFlood'));
      const composite=document.createElementNS(ns,'feComposite');composite.setAttribute('in2','SourceGraphic');composite.setAttribute('operator','in');
      filter.append(composite);svg.append(filter);document.body.append(svg);
    }
    filter.firstChild.setAttribute('flood-color',color);
    return `url("#${id}")`;
  }
  function restore(el,property){
    const original=el.__kixyuStyle[property];
    if(original.value)el.style.setProperty(property,original.value,original.priority);else el.style.removeProperty(property);
  }
  function apply(el,data,cfg,p){
    const properties=['filter','background-color','background-image','background-size','background-repeat','background-position'];
    if(!el.__kixyuStyle)el.__kixyuStyle=Object.fromEntries(properties.map(key=>[key,
      {value:el.style.getPropertyValue(key),priority:el.style.getPropertyPriority(key)}]));
    properties.forEach(key=>restore(el,key));
    delete el.dataset.kixyuInk;delete el.dataset.kixyuBacking;
    if(!data||getComputedStyle(el).filter!=='none')return;
    let background=rgb(cfg.background),parent=el;
    while(parent){
      const value=getComputedStyle(parent).backgroundColor.match(/[\d.]+/g);
      if(value&&(value.length<4||+value[3]>=.99)){background=value.slice(0,3).map(Number);break;}
      parent=parent.parentElement;
    }
    const page=luma(background);
    if(contrast(data.ink,page)>=p.MIN_CONTRAST)return;
    if(data.single){el.style.setProperty('filter',ownFilter(cfg,inkColor(cfg,page,p)),'important');el.dataset.kixyuInk='true';}
    else{
      const color=backing(data.ink,page,p);
      if(color){
        const style=getComputedStyle(el),width=el.clientWidth,height=el.clientHeight;
        const naturalWidth=el.naturalWidth||el.viewBox?.baseVal.width||width;
        const naturalHeight=el.naturalHeight||el.viewBox?.baseVal.height||height;
        const fit=style.objectFit==='contain'||style.objectFit==='scale-down';
        const scale=fit?Math.min(width/naturalWidth,height/naturalHeight,style.objectFit==='scale-down'?1:Infinity):1;
        el.style.setProperty('background-image',`linear-gradient(${color},${color})`,'important');
        el.style.setProperty('background-size',fit?`${naturalWidth*scale}px ${naturalHeight*scale}px`:'100% 100%','important');
        el.style.setProperty('background-repeat','no-repeat','important');
        el.style.setProperty('background-position',style.objectPosition||'center','important');
        el.dataset.kixyuBacking='true';
      }
    }
  }
  async function svgProfile(svg,p){
    if(svg.querySelector('image,foreignObject'))return null; // Do not guess external/mixed-media colors.
    const clone=svg.cloneNode(true),original=[svg,...svg.querySelectorAll('*')],copies=[clone,...clone.querySelectorAll('*')];
    original.forEach((el,i)=>{
      const style=getComputedStyle(el);
      for(const key of ['fill','stroke','opacity','fill-opacity','stroke-opacity'])copies[i].style.setProperty(key,style.getPropertyValue(key));
    });
    const image=new Image();image.src='data:image/svg+xml;charset=utf-8,'+encodeURIComponent(new XMLSerializer().serializeToString(clone));
    await Promise.race([image.decode(),new Promise((_,reject)=>setTimeout(()=>reject(new Error('decode timeout')),800))]);
    return profile(image,p);
  }
  window.kixyuImageTheme={async adapt(cfg){
    const run=++epoch,p=cfg.imageTonePolicy;
    if(!p)return;
    const elements=[...document.images,...Array.from(document.querySelectorAll('svg')).filter(el=>!el.dataset.kixyuDecoration)];
    const profiles=await Promise.all(elements.map(async el=>{
      try{
        if(el instanceof HTMLImageElement&&(!el.complete||!el.naturalWidth))return null;
        if(el.__kixyuProfile===undefined)el.__kixyuProfile=el instanceof HTMLImageElement
          ?profile(el,p):await svgProfile(el,p);
        return el.__kixyuProfile;
      }catch(_){return null;}
    }));
    if(run!==epoch)return;
    elements.forEach((el,i)=>apply(el,profiles[i],cfg,p));
  }};
})();
