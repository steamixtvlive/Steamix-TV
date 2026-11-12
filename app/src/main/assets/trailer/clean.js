(function () {
  function log(m) { try { Android.log(m); } catch (e) {} }

  function findVideo() {
    try {
      var v = document.querySelector('video');
      if (v) { return v; }
    } catch (e) {}
    try {
      var frames = document.querySelectorAll('iframe');
      for (var i = 0; i < frames.length; i++) {
        try {
          var doc = frames[i].contentDocument;
          if (doc) {
            var v2 = doc.querySelector('video');
            if (v2) { return v2; }
          }
        } catch (e) {}
      }
    } catch (e) {}
    return null;
  }

  function clean() {
    var v = findVideo();
    try {
      while (document.body.firstChild) {
        document.body.removeChild(document.body.firstChild);
      }
    } catch (e) {}
    try {
      document.body.style.background = '#000';
      document.body.style.margin = '0';
      document.body.style.overflow = 'hidden';
    } catch (e) {}

    if (typeof window.__svMuted === 'undefined') { window.__svMuted = false; }
    if (typeof window.__svPaused === 'undefined') { window.__svPaused = false; }
    window.__svNoVideo = true;
    if (!v) { return; }
    window.__svNoVideo = false;

    try {
      v.loop = true;
      // Ses durumu sadece uygulama tarafindan yonetilir. YouTube sayfa
      // yuklendikten sonra kendi mute ayarini geri uygulayabiliyor; bu yuzden
      // istenen durum her turda tekrar uygulanir.
      v.muted = !!window.__svMuted;
      if (!window.__svMuted) { v.volume = 1; }
      v.style.cssText = 'position:fixed;top:0;left:0;width:100vw;height:100vh;' +
        'object-fit:cover;z-index:1;background:#000;' +
        'filter:brightness(1.18) contrast(1.12) saturate(1.14);';
      document.body.appendChild(v);
    } catch (e) {}

    if (!window.__svPaused) {
      try {
        var p = v.play();
        if (p && p.catch) { p.catch(function () {}); }
      } catch (e) {}
    }

    try {
      v.addEventListener('ended', function () {
        try {
          v.currentTime = 0;
          if (!window.__svPaused) {
            var p2 = v.play();
            if (p2 && p2.catch) { p2.catch(function () {}); }
          }
        } catch (e) {}
      });
    } catch (e) {}

    try {
      v.addEventListener('playing', function () {
        log('watch-playing');
        try { Android.notifyPlaying(); } catch (e) {}
      });
    } catch (e) {}

    try {
      v.addEventListener('timeupdate', function () {
        try { if (v.currentTime > 0.3) { Android.notifyPlaying(); } } catch (e) {}
      });
    } catch (e) {}
  }

  window.__svClean = clean;
  log('clean-script-loaded');
})();
