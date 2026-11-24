import { test, expect } from '@playwright/test';
import fs from 'node:fs';
import { PNG } from 'pngjs';

const script = ['image-theme.js','reader.js'].map(name=>fs.readFileSync(new URL('../../main/assets/reader/browser/'+name, import.meta.url),'utf8')).join('\n');
const nativePolicy=fs.readFileSync(new URL('../../../../../core/core-reader-engine/src/main/java/com/kixyu9527/kixyubook/core/reader/engine/EpubImageTheme.kt',import.meta.url),'utf8');
const imageTonePolicy=Object.fromEntries(Array.from(nativePolicy.matchAll(/const val (\w+) = ([\d.]+)f?/g),m=>[m[1],Number(m[2])]));
const origin = 'https://epub.kixyu.invalid';
const token = 'test-session';
const settings = paragraphs => ({
  token, active: true, fixed: false, paged: true, animate: false, dark: false,
  background: '#ffffff', foreground: '#151515', accent: '#0066cc', margin: 24,
  fontSize: 22, lineHeight: 1.5, letterSpacing: 0, navigationVersion: 0, layoutVersion: 0, textVersion: 0,
  viewportWidth: 0, viewportHeight: 0, restore: {p: 0, c: 0}, paragraphs, annotations: [], search: '',
  imageTonePolicy,
});

async function open(page, body, configuration, styles = '') {
  await page.route('**/*', route => route.request().url() === `${origin}/OPS/body.xhtml`
    ? route.fulfill({contentType: 'text/html; charset=utf-8', body: `<!doctype html><html><head><meta charset="utf-8">
      <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; img-src data:; script-src 'none'">
      <style>${styles}</style></head><body>${body}</body></html>`}) : route.abort());
  await page.goto(`${origin}/OPS/body.xhtml`);
  await page.evaluate(() => {
    window.readerMessages = [];
    window.KixyuReaderBridge = {postMessage(value) {readerMessages.push(JSON.parse(value));}};
  });
  await page.evaluate(script);
  await page.evaluate(cfg => window.kixyuReader.open(cfg), configuration);
  await expect.poll(() => page.evaluate(() => readerMessages.some(m => m.type === 'ready'))).toBe(true);
}

async function latestPosition(page) {
  return page.evaluate(() => readerMessages.filter(m => m.type === 'position').at(-1));
}

function authoredFlow(flow) {
  // Playwright installs IPAGothic on Linux. The Linux default CJK fallback, WenQuanYi
  // Zen Hei, can give these glyphs zero vertical advance in headless Chromium.
  const font = flow.startsWith('writing-mode:vertical')
    ? ';font-family:IPAGothic,"Hiragino Sans","Yu Gothic","Noto Sans CJK JP",sans-serif' : '';
  return `body{${flow}${font}}`;
}

test('real Chromium preserves mid-paragraph anchors through dark/light and font reflow', async ({page}) => {
  const text = '文'.repeat(6000), cfg = settings([{index:0,text}]);
  await open(page, `<p>${text}</p>`, cfg);
  await page.evaluate(() => kixyuReader.turn(1));
  await expect.poll(async()=> (await latestPosition(page))?.c ?? 0).toBeGreaterThan(0);
  const before = await latestPosition(page);
  await page.evaluate(cfg => kixyuReader.update(cfg), {...cfg, dark:true, background:'#101010', foreground:'#eeeeee'});
  await expect.poll(async()=> (await latestPosition(page))?.c).toBe(before.c);
  await page.evaluate(cfg => kixyuReader.update(cfg), {...cfg, fontSize:28});
  await expect.poll(async()=> (await latestPosition(page))?.c).toBe(before.c);
  const visible = await page.evaluate(c => {
    const range=document.createRange(),node=document.querySelector('p').firstChild;
    range.setStart(node,c);range.setEnd(node,c+1);
    return Array.from(range.getClientRects()).some(r=>r.right>0&&r.left<innerWidth&&r.bottom>0&&r.top<innerHeight);
  },before.c);
  expect(visible).toBe(true);
});

test('search, note, underline and selection use the same original paragraph/character coordinates', async ({page}) => {
  const text='Alpha searchable note and underline omega.', cfg=settings([{index:7,text}]);
  cfg.restore={p:7,c:0}; cfg.search='searchable';
  cfg.annotations=[{p:7,start:17,end:21,text:'note',kind:'note'},{p:7,start:26,end:35,text:'underline',kind:'underline'}];
  await open(page, `<p>${text}</p>`, cfg);
  expect(await page.evaluate(()=>CSS.highlights.get('kixyu-search').size)).toBe(1);
  expect(await page.evaluate(()=>CSS.highlights.get('kixyu-note').size)).toBe(1);
  expect(await page.evaluate(()=>CSS.highlights.get('kixyu-underline').size)).toBe(1);
  await page.evaluate(()=>{
    const node=document.querySelector('p').firstChild, range=document.createRange();
    range.setStart(node,6);range.setEnd(node,16);getSelection().removeAllRanges();getSelection().addRange(range);
  });
  await expect.poll(()=>page.evaluate(()=>readerMessages.find(m=>m.type==='selection'))).toMatchObject({p:7,start:6,end:16});
});

test('personal corrections replace text safely without deleting the following illustration', async ({page}) => {
  const cfg=settings([{index:0,text:'Corrected paragraph.',sourceText:'Original paragraph.'},{index:1,text:'Next paragraph.'}]);
  await open(page, '<p>Original paragraph.</p><svg id="illustration" width="80" height="40"><rect width="80" height="40"/></svg><p>Next paragraph.</p>', cfg);
  await expect(page.locator('p').first()).toHaveText('Corrected paragraph.');
  await expect(page.locator('#illustration')).toHaveCount(1);
  await page.evaluate(cfg=>kixyuReader.update(cfg), {...cfg,textVersion:1,paragraphs:[{index:0,text:'Second correction.',sourceText:'Original paragraph.'},{index:1,text:'Next paragraph.'}]});
  await expect(page.locator('p').first()).toHaveText('Second correction.');
  await expect(page.locator('#illustration')).toHaveCount(1);
});

test('black transparent ink is relit without a white backing and restores its original style in light mode', async ({page}) => {
  const svg='<svg xmlns="http://www.w3.org/2000/svg" width="200" height="100"><rect x="30" y="20" width="140" height="60" fill="black"/></svg>';
  const image='data:image/svg+xml;base64,'+Buffer.from(svg).toString('base64');
  const cfg={...settings([{index:0,text:'Illustration.'}]),dark:true,background:'#101010',foreground:'#eeeeee'};
  await open(page, `<p>Illustration.</p><img id="art" src="${image}">`, cfg);
  await expect(page.locator('#art')).toHaveAttribute('data-kixyu-ink','true');
  await expect(page.locator('#art')).not.toHaveAttribute('data-kixyu-backing','true');
  expect(await page.locator('#art').evaluate(el=>getComputedStyle(el).backgroundColor)).toBe('rgba(0, 0, 0, 0)');
  const pixels=PNG.sync.read(await page.locator('#art').screenshot());
  const red=(x,y)=>pixels.data[(y*pixels.width+x)*4];
  expect(red(2,2)).toBeLessThan(25);
  expect(red(pixels.width/2|0,pixels.height/2|0)).toBeGreaterThan(175);
  expect(red(pixels.width/2|0,pixels.height/2|0)).toBeLessThan(220);
  await page.evaluate(cfg=>kixyuReader.update(cfg),{...cfg,dark:false,background:'#ffffff',foreground:'#151515'});
  await expect(page.locator('#art')).not.toHaveAttribute('data-kixyu-ink','true');
  expect(await page.locator('#art').evaluate(el=>getComputedStyle(el).filter)).toBe('none');
});

test('mixed-color transparent art gets a subdued backing, while opaque art and authored backing stay original',async({page})=>{
  const svg='<svg xmlns="http://www.w3.org/2000/svg" width="200" height="100"><path d="M30 20H100V80H30Z" fill="black"/><path d="M100 20H170V80H100Z" fill="#ff7020"/></svg>';
  const src='data:image/svg+xml;base64,'+Buffer.from(svg).toString('base64');
  const opaque='data:image/svg+xml;base64,'+Buffer.from(svg.replace('<path','<rect width="200" height="100" fill="white"/><path')).toString('base64');
  const cfg={...settings([{index:0,text:'Art.'}]),dark:true,background:'#101010',foreground:'#eeeeee'};
  await open(page,`<p>Art.</p><img id="color" src="${src}" style="width:200px;height:200px;object-fit:contain"><img id="opaque" src="${opaque}"><img id="backed" src="${src}" style="background-color:white">`,cfg);
  await expect(page.locator('#color')).toHaveAttribute('data-kixyu-backing','true');
  expect(await page.locator('#color').evaluate(el=>getComputedStyle(el).filter)).toBe('none');
  const image=PNG.sync.read(await page.locator('#color').screenshot());
  const at=(x,y)=>Array.from(image.data.slice((y*image.width+x)*4,(y*image.width+x)*4+3));
  expect(at(2,2)[0]).toBeLessThan(25); // Letterboxing belongs to the dark reader, not the backing.
  expect(at(2,60)[0]).toBeGreaterThan(70);expect(at(2,60)[0]).toBeLessThan(150);
  expect(at(120,100)).toEqual([255,112,32]);
  for(const id of ['opaque','backed']){
    await expect(page.locator('#'+id)).not.toHaveAttribute('data-kixyu-ink','true');
    await expect(page.locator('#'+id)).not.toHaveAttribute('data-kixyu-backing','true');
  }
});

test('fixed-layout pages retain authored geometry and native chapter boundaries', async ({page}) => {
  const cfg={...settings([]),fixed:true,viewportWidth:800,viewportHeight:600};
  await open(page,'<div id="plate" style="position:absolute;left:400px;top:300px;width:100px;height:50px;background:red"></div>',cfg);
  const bounds=await page.locator('#plate').boundingBox();
  expect(Math.abs(bounds.width/bounds.height-2)).toBeLessThan(.01);
  expect(bounds.width).toBeGreaterThan(30);
  await page.evaluate(()=>kixyuReader.turn(1));
  expect(await page.evaluate(()=>readerMessages.at(-1))).toMatchObject({type:'boundary',delta:1,token});
});

test('inactive preloaded leaves cannot publish progress or selection into the current book location', async ({page}) => {
  const cfg={...settings([{index:0,text:'Hidden paragraph.'}]),active:false};
  await openWithoutReady(page,'<p>Hidden paragraph.</p>',cfg);
  expect(await page.evaluate(()=>readerMessages.length)).toBe(0);
  await page.evaluate(cfg=>kixyuReader.update(cfg),{...cfg,active:true});
  await expect.poll(()=>page.evaluate(()=>readerMessages.some(m=>m.type==='ready'))).toBe(true);
});

for(const flow of ['direction:rtl','writing-mode:vertical-rl','writing-mode:vertical-lr']) {
  test(`authored ${flow} supports forward, backward and character restoration`,async({page})=>{
    const text='文章天地山海'.repeat(1000),cfg=settings([{index:0,text}]);
    await open(page,`<p>${text}</p>`,cfg,authoredFlow(flow));
    expect(await latestPosition(page)).toMatchObject({c:0,complete:false});
    await page.evaluate(()=>kixyuReader.turn(1));
    await expect.poll(async()=> (await latestPosition(page))?.c??0).toBeGreaterThan(0);
    const next=await latestPosition(page);
    const readyCount=await page.evaluate(()=>readerMessages.filter(m=>m.type==='ready').length);
    await page.evaluate(cfg=>kixyuReader.update(cfg),{...cfg,dark:true,background:'#151515',foreground:'#eeeeee'});
    await expect.poll(()=>page.evaluate(()=>readerMessages.filter(m=>m.type==='ready').length)).toBe(readyCount+1);
    expect((await latestPosition(page))?.c).toBe(next.c);
    await page.evaluate(()=>kixyuReader.turn(-1));
    await expect.poll(async()=> (await latestPosition(page))?.c).toBe(0);
  });
}

test('continuous vertical writing uses horizontal progress rather than skipping straight to another chapter',async({page})=>{
  const cfg={...settings([{index:0,text:'天地山海'.repeat(2000)}]),paged:false};
  await open(page,`<p>${cfg.paragraphs[0].text}</p>`,cfg,authoredFlow('writing-mode:vertical-rl'));
  expect(await latestPosition(page)).toMatchObject({c:0,complete:false});
  await page.evaluate(()=>kixyuReader.turn(1));
  await expect.poll(async()=> (await latestPosition(page))?.c??0).toBeGreaterThan(0);
  expect(await page.evaluate(()=>readerMessages.some(m=>m.type==='boundary'))).toBe(false);
  await page.evaluate(()=>kixyuReader.turn(-1));
  await expect.poll(()=>page.evaluate(()=>scrollX)).toBe(0);
});

test('select-all spanning paragraphs preserves both endpoints for safe copy-only native actions',async({page})=>{
  const cfg=settings([{index:3,text:'First paragraph.'},{index:4,text:'Second paragraph.'}]);cfg.restore={p:3,c:0};
  await open(page,'<p>First paragraph.</p><p>Second paragraph.</p>',cfg);
  await page.evaluate(()=>{
    const range=document.createRange();range.selectNodeContents(document.body);
    getSelection().removeAllRanges();getSelection().addRange(range);
  });
  await expect.poll(()=>page.evaluate(()=>readerMessages.filter(m=>m.type==='selection').at(-1)))
    .toMatchObject({p:3,start:0,endP:4,end:17});
  await page.evaluate(()=>kixyuReader.clearSelection());
  await expect.poll(()=>page.evaluate(()=>readerMessages.at(-1).type)).toBe('selection-clear');
  await page.evaluate(()=>{
    const node=document.querySelectorAll('p')[1].firstChild,r=document.createRange();
    r.setStart(node,0);r.setEnd(node,6);getSelection().addRange(r);
  });
  await expect.poll(()=>page.evaluate(()=>readerMessages.filter(m=>m.type==='selection').at(-1)))
    .toMatchObject({p:4,endP:4,start:0,end:6});
});

test('explicit same-chapter search navigation wins over in-flight theme and font updates',async({page})=>{
  const text='文'.repeat(6000),cfg=settings([{index:0,text}]);
  await open(page,`<p>${text}</p>`,cfg);
  await page.evaluate(cfg=>{
    kixyuReader.update({...cfg,dark:true,background:'#101010',foreground:'#eeeeee',fontSize:28});
    kixyuReader.update({...cfg,navigationVersion:1,restore:{p:0,c:4000},search:'文'});
  },cfg);
  await expect.poll(async()=> (await latestPosition(page))?.c).toBe(4000);
  const visible=await page.evaluate(()=>{
    const node=document.querySelector('p').firstChild,r=document.createRange();r.setStart(node,4000);r.setEnd(node,4001);
    return Array.from(r.getClientRects()).some(r=>r.right>0&&r.left<innerWidth&&r.bottom>0&&r.top<innerHeight);
  });
  expect(visible).toBe(true);
});

test('rapid turns target whole pages rather than adding a viewport to an in-flight animation offset',async({page})=>{
  const cfg={...settings([{index:0,text:'文'.repeat(9000)}]),animate:true};
  await open(page,`<p>${cfg.paragraphs[0].text}</p>`,cfg);
  await page.evaluate(()=>{kixyuReader.turn(1);kixyuReader.turn(1);kixyuReader.turn(1);});
  await expect.poll(()=>page.evaluate(()=>scrollX)).toBe(412*3);
  await page.evaluate(()=>{kixyuReader.turn(-1);kixyuReader.turn(-1);});
  await expect.poll(()=>page.evaluate(()=>scrollX)).toBe(412);
});

test('viewport rotation reflows to the actual saved character rather than the chapter start',async({page})=>{
  const cfg={...settings([{index:0,text:'文'.repeat(9000)}]),restore:{p:0,c:4000}};
  await open(page,`<p>${cfg.paragraphs[0].text}</p>`,cfg);
  await page.setViewportSize({width:820,height:412});
  await expect.poll(async()=> (await latestPosition(page))?.c).toBe(4000);
  await expect.poll(()=>page.evaluate(()=>{
    const r=document.createRange(),node=document.querySelector('p').firstChild;r.setStart(node,4000);r.setEnd(node,4001);
    return Array.from(r.getClientRects()).some(r=>r.left>=0&&r.right<=innerWidth&&r.top>=0&&r.bottom<=innerHeight);
  })).toBe(true);
});

async function openWithoutReady(page,body,cfg){
  await page.route('**/*',r=>r.fulfill({contentType:'text/html; charset=utf-8',body:`<html><head><meta charset="utf-8"></head><body>${body}</body></html>`}));
  await page.goto(`${origin}/OPS/body.xhtml`);
  await page.evaluate(()=>{window.readerMessages=[];window.KixyuReaderBridge={postMessage:v=>readerMessages.push(JSON.parse(v))};});
  await page.evaluate(script);await page.evaluate(cfg=>kixyuReader.open(cfg),cfg);
  await page.waitForTimeout(100);
}
