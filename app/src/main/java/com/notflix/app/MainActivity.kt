package com.notflix.app

import android.content.pm.ActivityInfo
import android.app.PictureInPictureParams
import android.util.Rational
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import java.security.MessageDigest
import android.view.KeyEvent
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.notflix.app.ui.AppRoot
import com.notflix.app.ui.components.VolumeHud
import com.notflix.app.ui.components.VolumeOverlay
import com.notflix.app.ui.screens.player.PlayerKeyBus
import com.notflix.app.ui.theme.NotflixTheme

class MainActivity : ComponentActivity() {

    companion object {
        private const val EXPECTED_SIGNATURE_SHA256 =
            "80cb863dbe927ba211c68817be0f3da8eb0d45df14546ffa795dc14a46924e4e"
    }

    private var activePlayer = false

    // Gercek PiP durumu (pencere olceginden degil, sistem bildiriminden).
    // Bu sayede kucuk ekranli TV'lerde kontroller yanlis likte kaybolmuyor.
    private val _pipActive = mutableStateOf(false)
    val pipActive: State<Boolean> get() = _pipActive

    private val audioManager: AudioManager by lazy {
        getSystemService(AUDIO_SERVICE) as AudioManager
    }
    private var lastAudibleVolume: Int = -1

    fun setPlayerActive(active: Boolean) { activePlayer = active }

    override fun onPictureInPictureModeChanged(
        inPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration
    ) {
        super.onPictureInPictureModeChanged(inPictureInPictureMode, newConfig)
        _pipActive.value = inPictureInPictureMode
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (activePlayer && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !isInPictureInPictureMode) {
            runCatching {
                enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        volumeControlStream = AudioManager.STREAM_MUSIC
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()
        if (!isSignatureValid() || !isEnvironmentSecure() || !isMinVersionOk() || !NativeBridge.isSecure()) {
            setContent {
                NotflixTheme {
                    Box(modifier = Modifier.fillMaxSize().background(Color.White))
                }
            }
            finish()
            return
        }
        setContent {
            NotflixTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .windowInsetsPadding(WindowInsets.displayCutout)
                ) {
                    AppRoot()
                    VolumeOverlay()
                }
            }
        }
    }

    // Hardware volume keys are consumed here so the Android system volume panel never appears;
    // the app draws its own centered indicator instead.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        // Bazi Google TV kumandalari ve set-top-box kumandalari yon tuslerini
        // SYSTEM_NAVIGATION_* koduyla gonderir; Compose bu kodlari tanimaz.
        // Onleri DPAD_*'a cevirerek kumanda her cihazda ayni calisir.
        val mapped = when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> KeyEvent.KEYCODE_DPAD_CENTER
            KeyEvent.KEYCODE_BUTTON_B -> KeyEvent.KEYCODE_BACK
            KeyEvent.KEYCODE_SYSTEM_NAVIGATION_UP -> KeyEvent.KEYCODE_DPAD_UP
            KeyEvent.KEYCODE_SYSTEM_NAVIGATION_DOWN -> KeyEvent.KEYCODE_DPAD_DOWN
            KeyEvent.KEYCODE_SYSTEM_NAVIGATION_LEFT -> KeyEvent.KEYCODE_DPAD_LEFT
            KeyEvent.KEYCODE_SYSTEM_NAVIGATION_RIGHT -> KeyEvent.KEYCODE_DPAD_RIGHT
            else -> 0
        }
        // Oynatıcı ekranı varsa kumanda/klavye tuşları odaktan bağımsız
        // burada yakalanır; kontroller gizli olsa bile tuşlar çalışır.
        // Basma, bırakma ve tekrar sayacı aynen iletilir (basılı tutarak sarma).
        val effective = if (mapped != 0) mapped else keyCode
        if (PlayerKeyBus.dispatch(effective, event.action, event.repeatCount)) {
            return true
        }
        // Emulator/PC keyboards: ESC acts as Back. If the on-screen keyboard is
        // open, the first press only dismisses it (TV remotes behave the same way).
        if (keyCode == KeyEvent.KEYCODE_ESCAPE) {
            if (event.action == KeyEvent.ACTION_UP) {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
                if (imm?.isAcceptingText == true) {
                    imm.hideSoftInputFromWindow(window.decorView.windowToken, 0)
                } else {
                    onBackPressedDispatcher.onBackPressed()
                }
            }
            return true
        }
        if (mapped != 0) {
            return super.dispatchKeyEvent(KeyEvent(event.action, mapped))
        }
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                val direction = if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                    AudioManager.ADJUST_RAISE
                } else {
                    AudioManager.ADJUST_LOWER
                }
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
                VolumeHud.show(streamLevel())
            }
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_VOLUME_MUTE) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                val maximum = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                if (current > 0) {
                    lastAudibleVolume = current
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
                } else {
                    val restore = lastAudibleVolume
                        .takeIf { it > 0 }
                        ?: (maximum / 2).coerceAtLeast(1)
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, restore, 0)
                }
                VolumeHud.show(streamLevel())
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun streamLevel(): Float {
        val maximum = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (maximum <= 0) return 0f
        return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maximum
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun isEnvironmentSecure(): Boolean {
        return try {
            if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) return false
            if (android.os.Build.TAGS != null && android.os.Build.TAGS.contains("test-keys")) return false
            if (android.os.Debug.isDebuggerConnected()) return false
            try { if (Settings.Secure.getInt(contentResolver, "adb_enabled", 0) == 1) return false } catch (e: Exception) {}
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun isMinVersionOk(): Boolean {
        return try {
            val url = "https://" + "raw.githubusercontent.com" + "/steamixtvlive/Steamix-TV/main/min_version.json"
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.requestMethod = "GET"
            if (conn.responseCode != 200) return true
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val min = org.json.JSONObject(text).optInt("minVersionCode", 0)
            if (min <= 0) return true
            val current = packageManager.getPackageInfo(packageName, 0).versionCode
            current >= min
        } catch (e: Exception) {
            true
        }
    }

    private fun isSignatureValid(): Boolean {
        return try {
            val info = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            val sig = info.signatures?.firstOrNull() ?: return false
            val md = MessageDigest.getInstance("SHA-256")
            md.update(sig.toByteArray())
            val hex = md.digest().joinToString("") { "%02x".format(it) }
            hex == EXPECTED_SIGNATURE_SHA256
        } catch (e: Exception) {
            false
        }
    }
}
