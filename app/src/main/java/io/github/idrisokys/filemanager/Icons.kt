package io.github.idrisokys.filemanager

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import androidx.core.graphics.PathParser

/** SVG path verisinden çizilen, XML gerektirmeyen ikon. */
class PathIcon(private val vp: Float, private val layers: List<Pair<String, Int>>) : Drawable() {
    private val items = layers.map { (d, c) ->
        Path(PathParser.createPathFromPathData(d)) to Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c; style = Paint.Style.FILL }
    }

    override fun draw(canvas: Canvas) {
        val b = bounds; if (b.isEmpty) return
        val sc = minOf(b.width(), b.height()) / vp
        canvas.save()
        canvas.translate(b.left + (b.width() - vp * sc) / 2f, b.top + (b.height() - vp * sc) / 2f)
        canvas.scale(sc, sc)
        items.forEach { (p, paint) -> canvas.drawPath(p, paint) }
        canvas.restore()
    }

    override fun setAlpha(a: Int) { items.forEach { it.second.alpha = a }; invalidateSelf() }
    override fun setColorFilter(cf: ColorFilter?) { items.forEach { it.second.colorFilter = cf }; invalidateSelf() }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

object Icons {
    private const val W = 0xFFFFFFFF.toInt()
    private fun m(d: String) = PathIcon(24f, listOf(d to W))

    /** Resimdeki klasör ikonu: koyu arka panel, beyaz kağıtlar, açık ön kapak. */
    fun folder() = PathIcon(100f, listOf(
        "M10,20 Q10,14 16,14 L38,14 Q42,14 45,18 L48,22 L84,22 Q90,22 90,28 L90,80 L10,80 Z" to 0xFFBD8640.toInt(),
        "M23,20 L75,20 Q77,20 77,22 L77,60 L23,60 Z" to 0xFFD5D5D5.toInt(),
        "M20,25 L72,25 Q74,25 74,27 L74,62 L20,62 Z" to 0xFFFFFFFF.toInt(),
        "M25,32 L69,32 L69,34.5 L25,34.5 Z M25,40 L69,40 L69,42.5 L25,42.5 Z M25,48 L69,48 L69,50.5 L25,50.5 Z" to 0xFFD5D5D5.toInt(),
        "M10,50 Q10,44 16,44 L58,44 Q62,44 65,41 L72,36 Q75,34 79,34 L84,34 Q90,34 90,40 L90,82 Q90,88 84,88 L16,88 Q10,88 10,82 Z" to 0xFFE2AC63.toInt()
    ))

    fun add() = m("M19,13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z")
    fun rename() = m("M3,17.25V21h3.75L17.81,9.94l-3.75,-3.75L3,17.25zM20.71,7.04c0.39,-0.39 0.39,-1.02 0,-1.41l-2.34,-2.34c-0.39,-0.39 -1.02,-0.39 -1.41,0l-1.83,1.83 3.75,3.75 1.83,-1.83z")
    fun delete() = m("M6,19c0,1.1 0.9,2 2,2h8c1.1,0 2,-0.9 2,-2V7H6v12zM19,4h-3.5l-1,-1h-5l-1,1H5v2h14V4z")
    fun share() = m("M18,16.08c-0.76,0 -1.44,0.3 -1.96,0.77L8.91,12.7c0.05,-0.23 0.09,-0.46 0.09,-0.7s-0.04,-0.47 -0.09,-0.7l7.05,-4.11c0.54,0.5 1.25,0.81 2.04,0.81 1.66,0 3,-1.34 3,-3s-1.34,-3 -3,-3 -3,1.34 -3,3c0,0.24 0.04,0.47 0.09,0.7L8.04,9.81C7.5,9.31 6.79,9 6,9c-1.66,0 -3,1.34 -3,3s1.34,3 3,3c0.79,0 1.5,-0.31 2.04,-0.81l7.12,4.16c-0.05,0.21 -0.08,0.43 -0.08,0.65 0,1.61 1.31,2.92 2.92,2.92 1.61,0 2.92,-1.31 2.92,-2.92s-1.31,-2.92 -2.92,-2.92z")
    fun move() = m("M20,6h-8l-2,-2H4C2.9,4 2.01,4.9 2.01,6L2,18c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V8C22,6.9 21.1,6 20,6zM12,17v-3H8v-2h4V9l4,4L12,17z")
    fun copy() = m("M16,1H4C2.9,1 2,1.9 2,3v14h2V3h12V1zM19,5H8C6.9,5 6,5.9 6,7v14c0,1.1 0.9,2 2,2h11c1.1,0 2,-0.9 2,-2V7C21,5.9 20.1,5 19,5zM19,21H8V7h11V21z")
    fun zip() = m("M20.54,5.23l-1.39,-1.68C18.88,3.21 18.47,3 18,3H6c-0.47,0 -0.88,0.21 -1.16,0.55L3.46,5.23C3.17,5.57 3,6.02 3,6.5V19c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2V6.5c0,-0.48 -0.17,-0.93 -0.46,-1.27zM12,17.5L6.5,12H10v-2h4v2h3.5L12,17.5zM5.12,5l0.81,-1h12l0.94,1H5.12z")
    fun unzip() = m("M20.55,5.22l-1.39,-1.68C18.88,3.21 18.47,3 18,3H6c-0.47,0 -0.88,0.21 -1.15,0.55L3.46,5.22C3.17,5.57 3,6.01 3,6.5V19c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2,-2V6.5c0,-0.49 -0.17,-0.93 -0.45,-1.28zM12,9.5l5.5,5.5H14v2h-4v-2H6.5L12,9.5zM5.12,5l0.82,-1h12l0.93,1H5.12z")
    fun paste() = m("M19,2h-4.18C14.4,0.84 13.3,0 12,0c-1.3,0 -2.4,0.84 -2.82,2H5c-1.1,0 -2,0.9 -2,2v16c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2V4c0,-1.1 -0.9,-2 -2,-2zM12,2c0.55,0 1,0.45 1,1s-0.45,1 -1,1 -1,-0.45 -1,-1 0.45,-1 1,-1zM19,20H5V4h2v3h10V4h2v16z")
    fun close() = m("M19,6.41L17.59,5 12,10.59 6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 12,13.41 17.59,19 19,17.59 13.41,12z")

    fun chevron() = m("M10,6L8.59,7.41 13.17,12l-4.58,4.59L10,18l6,-6z")

    fun homeColor() = PathIcon(24f, listOf(
        "M5,11 L12,5 L19,11 L19,21 L5,21 Z" to 0xFFF5F5F5.toInt(),
        "M12,2 L1,11.5 L2.6,13.2 L12,5.2 L21.4,13.2 L23,11.5 Z" to 0xFFE53935.toInt(),
        "M5,11 L12,5 L19,11 L19,21 L14,21 L14,14 L10,14 L10,21 L5,21 Z" to 0xFFEF5350.toInt(),
        "M10,14 L14,14 L14,21 L10,21 Z" to 0xFF29B6F6.toInt()
    ))

    /** Düz dosya: arkada katlanmış köşeli sayfa, önde beyaz kare. */
    fun file() = PathIcon(100f, listOf(
        "M30,8 L66,8 L90,32 L90,86 Q90,92 84,92 L36,92 Q30,92 30,86 Z" to 0xFFF2F2F2.toInt(),
        "M66,8 L90,32 L72,32 Q66,32 66,26 Z" to 0xFFCFCFCF.toInt(),
        "M16,24 L58,24 Q64,24 64,30 L64,74 Q64,80 58,80 L16,80 Q10,80 10,74 L10,30 Q10,24 16,24 Z" to 0xFFD8D8D8.toInt(),
        "M16,26 L58,26 Q62,26 62,30 L62,74 Q62,78 58,78 L16,78 Q12,78 12,74 L12,30 Q12,26 16,26 Z" to 0xFFFFFFFF.toInt()
    ))

    /** Mavi belge (md/txt/pdf…): mavi sayfa, önde beyaz kağıt ve koyu satırlar. */
    fun docBlue() = PathIcon(100f, listOf(
        "M20,8 L64,8 L88,32 L88,86 Q88,92 82,92 L26,92 Q20,92 20,86 Z" to 0xFF4F7CAC.toInt(),
        "M64,8 L88,32 L72,32 Q64,32 64,24 Z" to 0xFF7FB8E8.toInt(),
        "M10,34 L52,34 Q58,34 58,40 L58,76 Q58,82 52,82 L16,82 Q10,82 10,76 Z" to 0xFFFFFFFF.toInt(),
        "M16,44 L50,44 L50,47 L16,47 Z M16,52 L50,52 L50,55 L16,55 Z M16,60 L50,60 L50,63 L16,63 Z M16,68 L40,68 L40,71 L16,71 Z" to 0xFF2E3B5E.toInt()
    ))
}
