package com.notflix.app.ui.screens.player

import android.view.KeyEvent

/**
 * Oynatici ekraninin kumanda tuşlarını Activity seviyesinde yakalaması için
 * basit bir köprü.
 *
 * Sorun: Compose odak sistemi, kontroller gizlenince odak hedefini kaybediyor
 * ve tuşlar hiçbir yere ulaşamıyordu. Activity `dispatchKeyEvent` içinde
 * yakalanan tuşlar odaktan bağımsız çalışır; hem TV kumandası hem de
 * klavye (ok tuşları) aynı şekilde işlenir.
 */
object PlayerKeyBus {

    private var handler: ((Int) -> Boolean)? = null

    fun register(handler: (Int) -> Boolean) {
        this.handler = handler
    }

    fun unregister(handler: (Int) -> Boolean) {
        if (this.handler === handler) this.handler = null
    }

    /**
     * @return true ise tuş oynatıcı tarafından işlendi ve tüketildi.
     */
    fun dispatch(keyCode: Int): Boolean {
        val current = handler ?: return false
        return current(keyCode)
    }

    /** Kanal ileri/geri yardımcıları (kumandası olan TV'ler için). */
    val CHANNEL_KEYS = intArrayOf(
        KeyEvent.KEYCODE_CHANNEL_UP,
        KeyEvent.KEYCODE_CHANNEL_DOWN,
        KeyEvent.KEYCODE_MEDIA_NEXT,
        KeyEvent.KEYCODE_MEDIA_PREVIOUS
    )
}
