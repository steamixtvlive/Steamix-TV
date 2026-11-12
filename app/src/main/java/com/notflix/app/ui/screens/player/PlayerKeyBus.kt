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
 *
 * Birden fazla ekran (oynatici + detay) aynı anda kayitli olabilir; en son
 * kayit olan once denenir. Isleyen ilk handler tusi tuketir.
 */
object PlayerKeyBus {

    private val handlers = mutableListOf<(Int) -> Boolean>()

    fun register(handler: (Int) -> Boolean) {
        handlers.add(handler)
    }

    fun unregister(handler: (Int) -> Boolean) {
        handlers.removeAll { it === handler }
    }

    /**
     * @return true ise tuş işlendi ve tüketildi.
     */
    fun dispatch(keyCode: Int): Boolean {
        for (index in handlers.indices.reversed()) {
            if (handlers[index](keyCode)) return true
        }
        return false
    }

    /** Kanal ileri/geri yardımcıları (kumandası olan TV'ler için). */
    val CHANNEL_KEYS = intArrayOf(
        KeyEvent.KEYCODE_CHANNEL_UP,
        KeyEvent.KEYCODE_CHANNEL_DOWN,
        KeyEvent.KEYCODE_MEDIA_NEXT,
        KeyEvent.KEYCODE_MEDIA_PREVIOUS
    )
}
