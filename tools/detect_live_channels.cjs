// Canli TV kanal sagligi tespiti.
// Kural: 10 saniye icinde yanit vermeyen kanal -> once SD/diger varyantlar
// denenir; calisiyorsa o kaynak kullanilir, calismiyorsa kanal listeden cikarilir.
const fs = require('fs');
const HOST = 'http://131.123.43.89';
const ASSET = 'C:/Users/sehab/Desktop/NOTFLIX-Android/app/src/main/assets/live_channels.json';
const FB = 'C:/Users/sehab/Desktop/NOTFLIX-Android/app/src/main/assets/live_fallbacks.json';
const UA = 'Mozilla/5.0';
const TIMEOUT = 10000;      // akis yanit suresi (kullanici kurali)
const RESOLVE_TIMEOUT = 18000; // cozumleme sunucu tarafi (yavas olabiliyor)

async function resolve(url, ms = RESOLVE_TIMEOUT) {
  try {
    const ac = new AbortController();
    const t = setTimeout(() => ac.abort(), ms);
    const r = await fetch(HOST + '/live/resolve?url=' + encodeURIComponent(url), { signal: ac.signal, headers: { 'User-Agent': UA } });
    const txt = await r.text();
    clearTimeout(t);
    if (!r.ok) return null;
    try { const j = JSON.parse(txt); return j.url || j.stream || j.streamUrl || j.src || null; }
    catch { const m = txt.match(/https?:\/\/[^\s"']+/); return m ? m[0] : null; }
  } catch { return null; }
}

// 10 sn icinde akis geliyor mu? (master playlist + ilk segment)
async function alive(url, ms = TIMEOUT) {
  try {
    const ac = new AbortController();
    const t = setTimeout(() => ac.abort(), ms);
    const r = await fetch(url, { signal: ac.signal, headers: { 'User-Agent': UA, 'Range': 'bytes=0-65535' } });
    const body = (r.status === 200 || r.status === 206) ? await r.text() : '';
    clearTimeout(t);
    if (!body.includes('#EXTM3U') || body.includes('#EXT-X-ENDLIST')) return false;
    const seg = body.split('\n').find(l => l && !l.startsWith('#'));
    if (!seg) return false;
    const base = url.slice(0, url.lastIndexOf('/') + 1);
    const ac2 = new AbortController();
    const t2 = setTimeout(() => ac2.abort(), ms);
    const sr = await fetch(base + seg, { signal: ac2.signal, headers: { 'User-Agent': UA } });
    const buf = Buffer.from(await sr.arrayBuffer());
    clearTimeout(t2);
    return sr.ok && buf.length > 1000;
  } catch { return false; }
}

function q(name) {
  const n = String(name || '').toUpperCase();
  if (n.includes('4K')) return 6;
  if (n.includes('FHD')) return 5;
  if (n.includes('HD')) return 4;
  if (n.includes('720')) return 3;
  if (n.includes('SD') || n.includes('RAW')) return 2;   // SD/RAW tercih
  return 3;
}

(async () => {
  const root = JSON.parse(fs.readFileSync(ASSET, 'utf8'));
  const channels = root.channels || [];

  // Katalog: tvg(lower) -> varyant listesi (SD once)
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
      if (list.length < 14 && !list.some(v => v.url === it.url)) list.push({ url: it.url, name: it.name || '' });
    }
    cursor = json.nextCursor;
    if (!cursor || cursor === 'null') break;
  }
  console.log('katalog tvg: ' + variants.size + ' | kanal: ' + channels.length);

  const dead = [];
  let okCount = 0, fixedCount = 0;
  let idx = 0;
  async function worker() {
    while (idx < channels.length) {
      const c = channels[idx++];
      // 1) mevcut adres
      let chosen = null;
      const s0 = await resolve(c.u);
      if (s0 && await alive(s0)) {
        chosen = c.u;
      } else {
        // 2) ayni kanalin SD/diger varyantlari (once SD/RAW)
        const key = (c.t || '').toLowerCase();
        const list = (variants.get(key) || []).slice().sort((a, b) => q(a.name) - q(b.name));
        for (const v of list) {
          if (v.url === c.u) continue;
          const s = await resolve(v.url);
          if (!s) continue;
          if (await alive(s)) { chosen = v.url; break; }
        }
      }
      if (chosen) {
        okCount++;
        if (chosen !== c.u) { c.u = chosen; fixedCount++; console.log('\nDUZELTILDI ' + c.n + ' -> ' + chosen.slice(0, 60)); }
        else console.log('\nOK ' + c.n);
      } else {
        dead.push(c.n);
        console.log('\nOLU(10sn) ' + c.n);
      }
      process.stdout.write('.');
    }
  }
  await Promise.all(Array.from({ length: 5 }, worker));

  // 2. tur: olu cikanlara ikinci sans (kaynaklar dalgalaniyor)
  const stillDead = dead.slice();
  const retryable = channels.filter(c => stillDead.includes(c.n));
  dead.length = 0;
  const revived = [];
  idx = 0;
  async function worker2() {
    while (idx < retryable.length) {
      const c = retryable[idx++];
      const key = (c.t || '').toLowerCase();
      const list = (variants.get(key) || []).slice().sort((a, b) => q(a.name) - q(b.name));
      let chosen = null;
      for (const v of list) {
        const s = await resolve(v.url);
        if (!s) continue;
        if (await alive(s)) { chosen = v.url; break; }
      }
      if (chosen) {
        revived.push(c.n);
        c.u = chosen;
        c.a = [];
        console.log('\nCANLANDI ' + c.n);
      } else {
        dead.push(c.n);
      }
      process.stdout.write('.');
    }
  }
  await Promise.all(Array.from({ length: 4 }, worker2));
  okCount += revived.length;

  root.channels = channels.filter(c => !dead.includes(c.n));
  fs.writeFileSync(ASSET, JSON.stringify(root));
  fs.writeFileSync(FB, JSON.stringify({
    generatedAt: new Date().toISOString(),
    deadChannels: dead,
    fallbacks: {}
  }, null, 1));

  console.log('\n\ncalisan: ' + okCount + ' (duzeltilen: ' + fixedCount + ') | olu: ' + dead.length);
  console.log('listede kalan kanal: ' + root.channels.length);
  console.log('olu kanallar: ' + dead.join(' | '));
})();
