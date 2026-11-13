// Olu kanallarin yerine calisan kaynak bulur.
// Remote katalogdaki ayni kanalin (tvg) butun varyantlarini yoklar; calisan
// birini secer. SD / 720p onceliklidir (kullanici istegi).
const fs = require('fs');

const HOST = 'http://131.123.43.89';
const ASSET = 'C:/Users/sehab/Desktop/NOTFLIX-Android/app/src/main/assets/live_channels.json';
const FALLBACK_FILE = 'C:/Users/sehab/Desktop/NOTFLIX-Android/app/src/main/assets/live_fallbacks.json';
const OUT = 'C:/Users/sehab/Desktop/NOTflix-Android/app/src/main/assets/live_fallbacks.json';
const UA = 'Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36';

const asset = JSON.parse(fs.readFileSync(ASSET, 'utf8')).channels || [];
const prev = JSON.parse(fs.readFileSync(FALLBACK_FILE, 'utf8'));
const deadNames = new Set(prev.deadChannels || []);

function withTimeout(ms) {
  const ac = new AbortController();
  const t = setTimeout(() => ac.abort(), ms);
  return { signal: ac.signal, done: () => clearTimeout(t) };
}

async function getJson(url, ms = 15000) {
  try {
    const { signal, done } = withTimeout(ms);
    const res = await fetch(url, { signal, headers: { 'User-Agent': UA } });
    const txt = await res.text();
    done();
    return JSON.parse(txt);
  } catch (e) { return null; }
}

async function probe(url, ms = 9000) {
  try {
    const { signal, done } = withTimeout(ms);
    const res = await fetch(url, {
      signal, redirect: 'follow',
      headers: { 'User-Agent': UA, 'Range': 'bytes=0-131071' }
    });
    const body = (res.status === 200 || res.status === 206) ? await res.text() : '';
    done();
    return { ok: res.status === 200 || res.status === 206, body };
  } catch { return { ok: false, body: '' }; }
}

async function resolve(url) {
  try {
    const { signal, done } = withTimeout(12000);
    const res = await fetch(HOST + '/live/resolve?url=' + encodeURIComponent(url), { signal, headers: { 'User-Agent': UA } });
    const txt = await res.text();
    done();
    if (!res.ok) return null;
    try {
      const j = JSON.parse(txt);
      return j.url || j.stream || j.streamUrl || j.src || null;
    } catch {
      const m = txt.match(/https?:\/\/[^\s"']+/);
      return m ? m[0] : null;
    }
  } catch { return null; }
}

function maxResolution(m3u8) {
  const re = /RESOLUTION=(\d+)x(\d+)/g;
  let m, best = 0;
  while ((m = re.exec(m3u8))) {
    const h = parseInt(m[2], 10);
    if (h > best) best = h;
  }
  return best;
}

// isimden kalite tahmini (dusuk olan tercih edilir)
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
  // 1) Dead kanallarin tvg listesi (katalogda buyuk/kucuk harf farki var)
  const deadAssets = asset.filter(c => deadNames.has(c.n));
  const deadTvg = new Set(deadAssets.map(c => (c.t || '').toLowerCase()).filter(Boolean));
  console.log('olu kanal: ' + deadAssets.length + ', tvg sayisi: ' + deadTvg.size);

  // 2) Remote katalog: tvg -> varyantlar
  const variants = new Map();
  let cursor = null, guard = 0, total = 0;
  while (guard++ < 60) {
    const suffix = cursor ? ('?cursor=' + encodeURIComponent(cursor)) : '';
    const json = await getJson(HOST + '/live/catalog/live/channels.json' + suffix);
    if (!json) break;
    const items = json.items || json.metas || [];
    total += items.length;
    for (const it of items) {
      const tvg = it?.ids?.tvg;
      if (!tvg) continue;
      const key = String(tvg).toLowerCase();
      if (!deadTvg.has(key)) continue;
      const url = it.url;
      if (!url || !url.startsWith('http')) continue;
      if (!variants.has(key)) variants.set(key, []);
      const list = variants.get(key);
      if (list.length < 16 && !list.some(v => v.url === url)) {
        list.push({ url, name: it.name || '', id: it.id });
      }
    }
    cursor = json.nextCursor;
    if (!cursor || cursor === 'null') break;
  }
  console.log('katalog oge: ' + total + ', eslesen tvg: ' + variants.size);

  // 3) Her dead tvg icin varyantlari sirayla yokla (once dusuk kalite)
  const fallbacks = {};
  const tvgList = Array.from(variants.keys());
  let idx = 0;
  async function worker() {
    while (idx < tvgList.length) {
      const tvg = tvgList[idx++];
      const list = variants.get(tvg).slice().sort((a, b) => qualityScore(a.name) - qualityScore(b.name));
      for (const v of list) {
        const stream = await resolve(v.url);
        if (!stream) continue;
        const p = await probe(stream);
        if (!p.ok) continue;
        const height = p.body && p.body.includes('#EXTM3U') ? maxResolution(p.body) : 0;
        // Kalici olarak /live/play/ adresini sakliyoruz; uygulama her oynatmada
        // yeniden cozer (cozulmus stream adresleri zamanla olur).
        fallbacks[tvg] = { url: v.url, source: v.name, height: height || 0 };
        console.log('\nOK ' + tvg + ' <- ' + v.name + (height ? ' (' + height + 'p)' : ''));
        break;
      }
      process.stdout.write('.');
    }
  }
  await Promise.all(Array.from({ length: 6 }, worker));

  // 4) Yaz: olu kanalin tvg'sine gore fallback (onceki turun sonuclari da birlesir)
  const byChannel = {};
  // Onceki turlarda bulunmus, kalici (/live/play/) adresler korunur.
  let prevFallbacks = {};
  try {
    const prevRaw = JSON.parse(fs.readFileSync(FALLBACK_FILE, 'utf8'));
    prevFallbacks = prevRaw.fallbacks || {};
  } catch (e) { }
  for (const [name, v] of Object.entries(prevFallbacks)) {
    if (v && typeof v.url === 'string' && v.url.includes('/live/play/')) {
      byChannel[name] = v;
    }
  }
  for (const c of deadAssets) {
    const key = (c.t || '').toLowerCase();
    const fb = fallbacks[key];
    if (fb) byChannel[c.n] = { tvg: c.t, url: fb.url, source: fb.source, height: fb.height };
  }
  const out = {
    generatedAt: new Date().toISOString(),
    deadChannels: deadNames.size ? Array.from(deadNames) : [],
    fallbacks: byChannel
  };
  fs.writeFileSync(OUT, JSON.stringify(out, null, 1));
  console.log('\n\ncozulen olu kanal: ' + Object.keys(byChannel).length + ' / ' + deadAssets.length);
  console.log('yazildi: ' + OUT);
})();
