package io.github.idrisokys.filemanager

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import kotlin.math.min

/** Dosya yöneticisinde kullanılan basit, kod ile çizilen ikonlar. */
class GlyphDrawable(private val kind: Int, private val tint: Int = 0xFFFFFFFF.toInt()) : Drawable() {

    companion object {
        const val FOLDER = 0
        const val FILE = 1
        const val DOC = 2
        const val HOME = 3
        const val DRIVE = 4
        const val CHEVRON = 5
        const val PLUS = 6
    }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()

    private fun shade(col: Int, f: Float): Int {
        val a = col ushr 24
        val r = (((col shr 16) and 0xFF) * f).toInt()
        val g = (((col shr 8) and 0xFF) * f).toInt()
        val b = ((col and 0xFF) * f).toInt()
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun rr(c: Canvas, w: Float, h: Float, l: Float, t: Float, r: Float, b: Float, rad: Float, color: Int) {
        rect.set(l * w, t * h, r * w, b * h)
        p.style = Paint.Style.FILL
        p.color = color
        c.drawRoundRect(rect, rad * w, rad * w, p)
    }

    private fun poly(c: Canvas, w: Float, h: Float, color: Int, vararg pts: Float) {
        path.reset()
        path.moveTo(pts[0] * w, pts[1] * h)
        var i = 2
        while (i + 1 < pts.size) {
            path.lineTo(pts[i] * w, pts[i + 1] * h)
            i += 2
        }
        path.close()
        p.style = Paint.Style.FILL
        p.color = color
        c.drawPath(path, p)
    }

    override fun draw(c: Canvas) {
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        if (w <= 0f || h <= 0f) return
        c.save()
        c.translate(bounds.left.toFloat(), bounds.top.toFloat())
        when (kind) {
            FOLDER -> {
                rr(c, w, h, 0.02f, 0.10f, 0.46f, 0.40f, 0.06f, 0xFFC98A3F.toInt())   // sekme
                rr(c, w, h, 0.08f, 0.16f, 0.90f, 0.55f, 0.03f, 0xFFFFFFFF.toInt())   // kağıt 1
                rr(c, w, h, 0.13f, 0.24f, 0.96f, 0.60f, 0.03f, 0xFFE6E6E6.toInt())   // kağıt 2
                rr(c, w, h, 0.00f, 0.38f, 1.00f, 0.95f, 0.07f, 0xFFE3A95D.toInt())   // ön yüz
            }
            FILE, DOC -> {
                rr(c, w, h, 0.04f, 0.14f, 0.62f, 0.98f, 0.06f, (tint and 0x00FFFFFF) or (0x99 shl 24))
                poly(c, w, h, tint, 0.22f, 0.02f, 0.64f, 0.02f, 0.92f, 0.30f, 0.92f, 0.92f, 0.22f, 0.92f)
                poly(c, w, h, shade(tint, 0.8f), 0.64f, 0.02f, 0.64f, 0.30f, 0.92f, 0.30f)
                if (kind == DOC) {
                    for (y in listOf(0.46f, 0.58f, 0.70f)) {
                        rr(c, w, h, 0.32f, y, 0.82f, y + 0.05f, 0.02f, 0xFFFFFFFF.toInt())
                    }
                }
            }
            HOME -> {
                poly(c, w, h, 0xFFE53935.toInt(), 0.5f, 0.04f, 1.0f, 0.50f, 0.0f, 0.50f)   // çatı
                rr(c, w, h, 0.14f, 0.46f, 0.86f, 0.96f, 0.04f, 0xFFF5F5F5.toInt())          // gövde
                rr(c, w, h, 0.26f, 0.56f, 0.46f, 0.72f, 0.02f, 0xFF42A5F5.toInt())          // pencere
                rr(c, w, h, 0.56f, 0.62f, 0.74f, 0.96f, 0.02f, 0xFFE53935.toInt())          // kapı
            }
            DRIVE -> {
                rr(c, w, h, 0.05f, 0.12f, 0.95f, 0.92f, 0.12f, 0xFFD0D0D0.toInt())
                rr(c, w, h, 0.05f, 0.68f, 0.95f, 0.92f, 0.12f, 0xFFB2B2B2.toInt())
                p.style = Paint.Style.FILL
                p.color = 0xFF4CAF50.toInt()
                c.drawCircle(0.22f * w, 0.80f * h, 0.05f * w, p)
            }
            CHEVRON -> {
                p.style = Paint.Style.STROKE
                p.strokeWidth = 0.09f * w
                p.strokeCap = Paint.Cap.ROUND
                p.strokeJoin = Paint.Join.ROUND
                p.color = tint
                path.reset()
                path.moveTo(0.35f * w, 0.22f * h)
                path.lineTo(0.65f * w, 0.50f * h)
                path.lineTo(0.35f * w, 0.78f * h)
                c.drawPath(path, p)
                p.style = Paint.Style.FILL
            }
            PLUS -> {
                p.style = Paint.Style.FILL
                p.color = tint
                c.drawCircle(w / 2f, h / 2f, min(w, h) / 2f, p)
                p.color = 0xFFFFFFFF.toInt()
                rect.set(0.28f * w, 0.46f * h, 0.72f * w, 0.54f * h)
                c.drawRect(rect, p)
                rect.set(0.46f * w, 0.28f * h, 0.54f * w, 0.72f * h)
                c.drawRect(rect, p)
            }
        }
        c.restore()
    }

    override fun setAlpha(alpha: Int) {}

    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Suppress("DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
