// NOTFLİX canlı TV kanal sağlığı yoklaması.
// Amaç: çekmeyen (ölü) kanalları bulup, onların yerine çalışan kaynakları bulmak.
// Çalışan kaynak SD/720p üstüne çıkmamalı (kullanıcı isteği).
const fs = require('fs');
const path = require('path');

const HOST = 'http://131.123.43.89';
const ASSET = 'C:/Users/sehab/Desktop/NOTFLIX-Android/app/src/main/assets/live_channels.json';
const OUT = 'C:/Users/sehab/Desktop/NOTFLIX-Android/app/src/main/assets/live_fallbacks.json';
const UA = 'Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36';

const list = JSON.parse(fs.readFileSync(ASSET, 'utf8')).channels || [];

function withTimeout(ms) {
  const ac = new AbortController();
  const t = setTimeout(() => ac.abort(), ms);
  return { signal: ac.signal, done: () => clearTimeout(t) };
}

async function probe(url, ms = 9000) {
  try {
    const { signal, done } = withTimeout(ms);
    const res = await fetch(url, {
      signal,
      redirect: 'follow',
      headers: { 'User-Agent': UA, 'Range': 'bytes=0-262143' }
    });
    const body = res.status === 200 || res.status === 206 ? await res.text() : '';
    done();
    return { ok: res.status === 200 || res.status === 206, status: res.status, body, url: res.url };
  } catch (e) {
    return { ok: false, status: 0, body: '', error: String(e.message || e) };
  }
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
  } catch {
    return null;
  }
}

// m3u8 icinden en yuksek cozunurlugu bul
function maxResolution(m3u8) {
  const re = /RESOLUTION=(\d+)x(\d+)/g;
  let m, best = 0;
  while ((m = re.exec(m3u8))) {
    const h = parseInt(m[2], 10);
    if (h > best) best = h;
  }
  return best;
}

async function checkChannel(ch) {
  const candidates = [];
  if (ch.s && ch.s.startsWith('http')) candidates.push({ url: ch.s, kind: 'direct' });
  for (const a of (ch.a || [])) if (String(a).startsWith('http')) candidates.push({ url: a, kind: 'alt' });

  let primaryStream = null;
  if (ch.u && ch.u.startsWith('http')) {
    primaryStream = await resolve(ch.u);
    if (primaryStream) candidates.unshift({ url: primaryStream, kind: 'resolved' });
  }

  const results = [];
  for (const c of candidates) {
    const p = await probe(c.url);
    let height = 0;
    if (p.ok && p.body && p.body.includes('#EXTM3U')) height = maxResolution(p.body);
    results.push({ url: c.url, kind: c.kind, ok: p.ok, status: p.status, height, len: p.body ? p.body.length : 0 });
    if (p.ok) break; // ilk calisan yeter
  }
  return { name: ch.n, u: ch.u, primaryOk: results.find(r => r.kind === 'resolved')?.ok === true, results };
}

async function pool(items, limit, fn) {
  const out = [];
  let idx = 0;
  async function worker() {
    while (idx < items.length) {
      const i = idx++;
      try { out[i] = await fn(items[i]); } catch (e) { out[i] = { error: String(e) }; }
      process.stdout.write('.');
    }
  }
  await Promise.all(Array.from({ length: limit }, worker));
  return out;
}

(async () => {
  console.log('toplam kanal: ' + list.length);
  const res = await pool(list, 8, checkChannel);
  const dead = [];
  const fixed = {};
  for (const r of res) {
    if (!r || r.error) continue;
    const working = (r.results || []).find(x => x.ok);
    if (!working) {
      dead.push(r.name);
    } else if (!r.primaryOk) {
      // primer link olu mu, alternatifi/stream calisiyor mu
      fixed[r.name] = { url: working.url, kind: working.kind, height: working.height };
    }
  }
  console.log('\nolu kanal sayisi: ' + dead.length);
  console.log('duzeltilmesi gereken: ' + Object.keys(fixed).length);
  fs.writeFileSync(OUT, JSON.stringify({
    generatedAt: new Date().toISOString(),
    deadChannels: dead,
    fallbacks: fixed
  }, null, 1));
  console.log('yazildi: ' + OUT);
})();
