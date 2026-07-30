package com.iblu01.portallauncher.ui.apps

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** Square edge, in px, every launcher icon is rasterized to. */
const val ICON_SIZE_PX = 144

/**
 * Rasterizes a launcher icon to a fixed square. Handles adaptive icons (which carry no bitmap of
 * their own) by drawing them into a canvas.
 *
 * Always call this off the main thread: for app icons it reaches into the PackageManager's
 * resources, which is disk I/O.
 */
internal fun Drawable.toAndroidBitmap(sizePx: Int = ICON_SIZE_PX): Bitmap {
    (this as? BitmapDrawable)?.bitmap?.let { source ->
        return Bitmap.createScaledBitmap(source, sizePx, sizePx, true)
    }
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    setBounds(0, 0, sizePx, sizePx)
    draw(Canvas(bitmap))
    return bitmap
}

internal fun Drawable.toImageBitmap(sizePx: Int = ICON_SIZE_PX): ImageBitmap =
    toAndroidBitmap(sizePx).asImageBitmap()
