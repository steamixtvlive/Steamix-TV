(function () {
  function log(m) { try { Android.log(m); } catch (e) {} }

  function moviePlayer() {
    try { return document.getElementById('movie_player'); } catch (e) { return null; }
  }

  function ytPlayer() {
    try { return window.__player; } catch (e) { return null; }
  }

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

  function apply(muted, play) {
    var applied = false;
    // Istenen durum sayfada saklanir; temizleme scripti bunu tekrar uygular.
    window.__svMuted = !!muted;
    window.__svPaused = !play;

    // 1) YouTube sayfa oynaticisi: kendi ses/oynatma durumunu gunceller.
    var m = moviePlayer();
    if (m) {
      try {
        if (muted) { m.mute(); } else { m.unMute(); m.setVolume(100); }
      } catch (e) {}
      try {
        if (play) { m.playVideo(); } else { m.pauseVideo(); }
      } catch (e) {}
      applied = true;
    }

    // 2) YouTube iframe API.
    var y = ytPlayer();
    if (y) {
      try {
        if (muted) { y.mute(); } else { y.unMute(); y.setVolume(100); }
      } catch (e) {}
      try {
        if (play) { y.playVideo(); } else { y.pauseVideo(); }
      } catch (e) {}
      applied = true;
    }

    // 3) Ham <video> elementi.
    var v = findVideo();
    if (v) {
      try {
        window.__svMuted = muted;
        window.__svPaused = !play;
        v.muted = !!muted;
        if (!muted) { v.volume = 1; }
        if (play) {
          var p = v.play();
          if (p && p.catch) { p.catch(function () {}); }
        } else {
          v.pause();
        }
        applied = true;
      } catch (e) {}
    }

    log('control muted=' + muted + ' play=' + play + ' applied=' + applied);
    return applied;
  }

  window.__svControl = apply;
  log('control-script-loaded');
})();
