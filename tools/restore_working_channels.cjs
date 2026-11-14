// Cekmeyen kanallari tekrar yoklar; calisanlari listede geri koyar.
// Calisan kanalin adresi dogrudan /live/play/ olur (hizli acar).
const fs = require('fs');
const HOST = 'http://131.123.43.89';
const ASSET = 'C:/Users/sehab/Desktop/NOTFLIX-Android/app/src/main/assets/live_channels.json';
const FB = 'C:/Users/sehab/Desktop/NOTFLIX-Android/app/src/main/assets/live_fallbacks.json';
const UA = 'Mozilla/5.0';

async function resolve(url) {
  try {
    const ac = new AbortController();
    const t = setTimeout(() => ac.abort(), 12000);
    const r = await fetch(HOST + '/live/resolve?url=' + encodeURIComponent(url), { signal: ac.signal, headers: { 'User-Agent': UA } });
    const txt = await r.text();
    clearTimeout(t);
    if (!r.ok) return null;
    try { const j = JSON.parse(txt); return j.url || j.stream || j.streamUrl || j.src || null; }
    catch { const m = txt.match(/https?:\/\/[^\s"']+/); return m ? m[0] : null; }
  } catch { return null; }
}

async function works(url) {
  try {
    const ac = new AbortController();
    const t = setTimeout(() => ac.abort(), 9000);
    const r = await fetch(url, { signal: ac.signal, headers: { 'User-Agent': UA, 'Range': 'bytes=0-65535' } });
    const body = (r.status === 200 || r.status === 206) ? await r.text() : '';
    clearTimeout(t);
    if (!body.includes('#EXTM3U')) return false;
    return !body.includes('#EXT-X-ENDLIST');
  } catch { return false; }
}

function qualityScore(name) {
  const n = String(name || '').toUpperCase();
  if (n.includes('4K')) return 5;
  if (n.includes('FHD')) return 4;
  if (n.includes('HD')) return 3;
  if (n.includes('720')) return 2;
  if (n.includes('SD') || n.includes('RAW')) return 1;
  return 2;
}

(async () => {
  const root = JSON.parse(fs.readFileSync(ASSET, 'utf8'));
  const fbFile = JSON.parse(fs.readFileSync(FB, 'utf8'));
  const deadNames = new Set(fbFile.deadChannels || []);

  // Katalog: tvg(lower) -> varyantlar
  const variants = new Map();
  let cursor = null, pages = 0;
  while (pages++ < 60) {
    const suffix = cursor ? ('?cursor=' + encodeURIComponent(cursor)) : '';
    let json = null;
    try {
      const r = await fetch(HOST + '/live/catalog/live/channels.json' + suffix, { headers: { 'User-Agent': UA } });
      if (!r.ok) break;
      json = await r.json();
    } catch { break; }
    for (const it of (json.items || [])) {
      const tvg = (it.ids && it.ids.tvg) || '';
      if (!tvg || !it.url || !it.url.startsWith('http')) continue;
      const key = tvg.toLowerCase();
      if (!variants.has(key)) variants.set(key, []);
      const list = variants.get(key);
      if (list.length < 12 && !list.some(v => v.url === it.url)) list.push({ url: it.url, name: it.name || '' });
    }
    cursor = json.nextCursor;
    if (!cursor || cursor === 'null') break;
  }
  console.log('katalog tvg: ' + variants.size);

  const dead = root.channels.filter(c => deadNames.has(c.n));
  console.log('yoklanacak olu kanal: ' + dead.length);

  const restored = {};
  const stillDead = [];
  let idx = 0;
  async function worker() {
    while (idx < dead.length) {
      const c = dead[idx++];
      const cands = [];
      // 1) kanalin kendi adresi + mevcut alternatifleri
      if (c.u) cands.push(c.u);
      for (const a of (c.a || [])) if (a.includes('/live/play/')) cands.push(a);
      // 2) katalogdaki ayni kanalin diger varyantlari (once dusuk kalite)
      const key = (c.t || '').toLowerCase();
      const list = (variants.get(key) || []).slice().sort((a, b) => qualityScore(a.name) - qualityScore(b.name));
      for (const v of list) cands.push(v.url);

      let found = null;
      for (const u of cands) {
        if (!u || u === found) continue;
        const stream = await resolve(u);
        if (!stream) continue;
        if (await works(stream)) { found = u; break; }
      }
      if (found) { restored[c.n] = found; console.log('\nOK ' + c.n + ' <- ' + found.slice(0, 70)); }
      else { stillDead.push(c.n); console.log('\nOLU ' + c.n); }
      process.stdout.write('.');
    }
  }
  await Promise.all(Array.from({ length: 6 }, worker));

  // Calisan kanallar: adres dogrudan calisan kaynak olur (hizli acar)
  for (const c of root.channels) {
    const u = restored[c.n];
    if (!u) continue;
    c.u = u;
    c.a = [];
  }
  fs.writeFileSync(ASSET, JSON.stringify(root));

  const out = {
    generatedAt: new Date().toISOString(),
    deadChannels: stillDead,
    fallbacks: {}
  };
  fs.writeFileSync(FB, JSON.stringify(out, null, 1));

  console.log('\n\ngeri gelen kanal: ' + Object.keys(restored).length + ' | listede kalmayan: ' + stillDead.length);
  console.log('toplam kanal: ' + root.channels.length);
})();
