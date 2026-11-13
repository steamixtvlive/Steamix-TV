// Her kanala ayni kanalin (tvg) diger varyantlarini alternatif olarak ekler.
// Boylece bir kaynak olunce digerleri devreye girer (TRT Avaz, TRT Turk vb.).
const fs = require('fs');
const HOST = 'http://131.123.43.89';
const ASSET = 'C:/Users/sehab/Desktop/NOTFLIX-Android/app/src/main/assets/live_channels.json';
const UA = 'Mozilla/5.0';

function qualityScore(name) {
  const n = String(name || '').toUpperCase();
  if (n.includes('4K')) return 5;
  if (n.includes('FHD')) return 4;
  if (n.includes('HD')) return 3;
  if (n.includes('720')) return 2;
  if (n.includes('SD') || n.includes('RAW')) return 1;
  return 2;
}

// Bazi tvg degerlerinde tekrar var ("trtrtavaz.tr" -> "trtavaz.tr").
function simplify(t) {
  return String(t || '').toLowerCase().replace(/^trttrt/, 'trt');
}

(async () => {
  const root = JSON.parse(fs.readFileSync(ASSET, 'utf8'));
  const channels = root.channels || [];

  // Katalog: tvg(lower) -> varyantlar
  const variants = new Map();
  let cursor = null, pages = 0;
  while (pages++ < 60) {
    const suffix = cursor ? ('?cursor=' + encodeURIComponent(cursor)) : '';
    const r = await fetch(HOST + '/live/catalog/live/channels.json' + suffix, { headers: { 'User-Agent': UA } });
    if (!r.ok) break;
    const j = await r.json();
    for (const it of (j.items || [])) {
      const tvg = (it.ids && it.ids.tvg) || '';
      if (!tvg || !it.url || !it.url.startsWith('http')) continue;
      const key = tvg.toLowerCase();
      for (const k of Array.from(new Set([key, simplify(tvg)]))) {
        if (!variants.has(k)) variants.set(k, []);
        const list = variants.get(k);
        if (list.length < 8 && !list.some(v => v.url === it.url)) {
          list.push({ url: it.url, name: it.name || '' });
        }
      }
    }
    cursor = j.nextCursor;
    if (!cursor || cursor === 'null') break;
  }
  console.log('katalog tvg: ' + variants.size);

  let added = 0;
  for (const c of channels) {
    const key = (c.t || '').toLowerCase();
    const exact = variants.get(key) || [];
    const loose = variants.get(simplify(c.t)) || [];
    const seen = new Set();
    const list = exact.concat(loose).filter(v => {
      if (seen.has(v.url)) return false;
      seen.add(v.url);
      return true;
    });
    if (!list || !list.length) continue;
    const sorted = list.slice().sort((a, b) => qualityScore(a.name) - qualityScore(b.name));
    const own = new Set(c.a || []);
    const existingVariants = (c.a || []).filter(u => u.includes('/live/play/'));
    const legacy = (c.a || []).filter(u => !u.includes('/live/play/'));
    // Once katalog varyantlari (canli calisir), sonra eski dogrudan adresler.
    const extra = sorted.map(v => v.url).filter(u => u !== c.u && !own.has(u)).slice(0, 5);
    const merged = Array.from(new Set([...existingVariants, ...extra, ...legacy])).slice(0, 8);
    if (merged.length) {
      c.a = merged;
      added++;
    }
  }
  console.log('alternatifi genisletilen kanal: ' + added + '/' + channels.length);
  fs.writeFileSync(ASSET, JSON.stringify(root));
  console.log('yazildi: ' + ASSET);
})();
