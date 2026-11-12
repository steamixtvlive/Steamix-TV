package com.notflix.app

import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.notflix.app.ui.AppRoot
import com.notflix.app.ui.components.VolumeHud
import com.notflix.app.ui.components.VolumeOverlay
import com.notflix.app.ui.theme.NotflixTheme

class MainActivity : ComponentActivity() {

    private val audioManager: AudioManager by lazy {
        getSystemService(AUDIO_SERVICE) as AudioManager
    }
    private var lastAudibleVolume: Int = -1

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
        // Gamepad A activates the focused item like D-pad center, B acts as Back.
        val mapped = when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> KeyEvent.KEYCODE_DPAD_CENTER
            KeyEvent.KEYCODE_BUTTON_B -> KeyEvent.KEYCODE_BACK
            else -> 0
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
}
