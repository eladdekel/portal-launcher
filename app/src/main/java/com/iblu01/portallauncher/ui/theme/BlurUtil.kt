package com.iblu01.portallauncher.ui.theme

import android.graphics.Bitmap
import kotlin.math.max

/**
 * Cheap, dependency-free bitmap blur for API < 31 (where [androidx.compose.ui.draw.blur]
 * is a no-op). Works by downscaling then bilinear-upscaling — the interpolation smears
 * detail into a soft, StandBy-style frost — followed by a light box pass to kill banding.
 *
 * This is deliberately not RenderScript: RS is deprecated as of API 31 and overkill for a
 * static wallpaper that is blurred once per image change.
 */
fun blurBitmapCompat(source: Bitmap, radius: Int = 32): Bitmap {
    if (radius <= 0 || source.width < 2 || source.height < 2) return source

    // Downscale factor grows with radius; clamp so we never go below a usable size.
    val factor = (radius / 4).coerceIn(4, 16)
    val w = max(1, source.width / factor)
    val h = max(1, source.height / factor)

    val small = Bitmap.createScaledBitmap(source, w, h, true)
    val boxed = boxBlur(small)
    // Upscale back to a modest working size; Compose stretches it over the full screen.
    val outW = max(1, source.width / 2)
    val outH = max(1, source.height / 2)
    val result = Bitmap.createScaledBitmap(boxed, outW, outH, true)
    if (boxed != small) small.recycle()
    if (result != boxed) boxed.recycle()
    return result
}

/** One-pass 3x3 box blur on a small bitmap. Averages each pixel with its neighbours. */
private fun boxBlur(src: Bitmap): Bitmap {
    val w = src.width
    val h = src.height
    if (w < 3 || h < 3) return src
    val pixels = IntArray(w * h)
    src.getPixels(pixels, 0, w, 0, 0, w, h)
    val out = IntArray(w * h)
    for (y in 0 until h) {
        for (x in 0 until w) {
            var a = 0; var r = 0; var g = 0; var b = 0; var n = 0
            for (dy in -1..1) {
                val yy = y + dy
                if (yy < 0 || yy >= h) continue
                for (dx in -1..1) {
                    val xx = x + dx
                    if (xx < 0 || xx >= w) continue
                    val p = pixels[yy * w + xx]
                    a += (p ushr 24) and 0xFF
                    r += (p ushr 16) and 0xFF
                    g += (p ushr 8) and 0xFF
                    b += p and 0xFF
                    n++
                }
            }
            out[y * w + x] = ((a / n) shl 24) or ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
        }
    }
    val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    result.setPixels(out, 0, w, 0, 0, w, h)
    return result
}
