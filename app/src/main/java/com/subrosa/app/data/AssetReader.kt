package com.subrosa.app.data

/**
 * Reads a named asset to a String. Abstracted so data-layer classes can be unit-tested off-device
 * (a test passes a reader backed by the filesystem; the app passes one backed by [android.content.res.AssetManager]).
 */
fun interface AssetReader {
    fun read(name: String): String
}
