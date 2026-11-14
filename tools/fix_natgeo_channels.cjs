// National Geographic / Nat Geo Wild kanal kaynaklarini duzeltir.
// Sorun: "Nat Geo" kanali TrNationalGeographic.tr (4K varyanti) kaynagini
// kullaniyordu; saglayici bu kaynagi Nat Geo Wild akisina baglamisti.
// Dogru varyantlar: NationalGeographic.tr ve NatGeoWild.tr.
const fs = require('fs');
const ASSET = 'C:/Users/sehab/Desktop/NOTFLIX-Android/app/src/main/assets/live_channels.json';

const FIX = {
  'Nat Geo': {
    t: 'nationalgeographic.tr',
    urls: [
      'https://www.vavoo.to/live/play/1514492628', // NATIONAL GEOGRAPHIC HD+ (1)
      'https://www.vavoo.to/live/play/1462574321', // NATIONAL GEOGRAPHIC HD (1)
      'https://www.vavoo.to/live/play/1845660630'  // NAT GEO (6)
    ]
  },
  'Nat Geo Wild': {
    t: 'natgeowild.tr',
    urls: [
      'https://www.vavoo.to/live/play/1519237116', // NAT GEO WILD HD (1)
      'https://www.vavoo.to/live/play/1999823186', // NAT GEO WILD (7)
      'https://www.vavoo.to/live/play/124345657'   // NAT GEO WILD HD+ (1)
    ]
  }
};

const root = JSON.parse(fs.readFileSync(ASSET, 'utf8'));
for (const channel of root.channels) {
  const fix = FIX[channel.n];
  if (!fix) continue;
  channel.t = fix.t;
  channel.u = fix.urls[0];
  channel.a = fix.urls.slice(1);
  console.log('DUZELTILDI ' + channel.n + ' -> ' + fix.t + ' | ' + fix.urls[0]);
}
fs.writeFileSync(ASSET, JSON.stringify(root));
console.log('bitti, kanal: ' + root.channels.length);
