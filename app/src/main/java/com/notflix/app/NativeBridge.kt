package com.notflix.app

object NativeBridge {
    init {
        System.loadLibrary("notflixnative")
    }
    external fun isSecure(): Boolean
}
