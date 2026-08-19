package app.aaps.plugins.main.general.statsSummary

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * Community patch — barre horizontale à deux segments (basal / bolus).
 *
 * Purement décorative : n'affiche que des valeurs qu'on lui passe.
 */
class SplitBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {

        const val COLOR_BASAL = 0xFF26C6DA.toInt()
        const val COLOR_BOLUS = 0xFF7E57C2.toInt()
        private const val COLOR_EMPTY = 0x33888888
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    private var firstPercent: Int? = null

    /** @param percent part du premier segment (basal), 0-100, ou null si inconnu */
    fun setValue(percent: Int?) {
        firstPercent = percent
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val radius = h / 3f

        val p = firstPercent
        if (p == null) {
            paint.color = COLOR_EMPTY
            rect.set(0f, 0f, w, h)
            canvas.drawRoundRect(rect, radius, radius, paint)
            return
        }

        canvas.save()
        rect.set(0f, 0f, w, h)
        val clip = Path()
        clip.addRoundRect(rect, radius, radius, Path.Direction.CW)
        canvas.clipPath(clip)

        val split = w * p.coerceIn(0, 100) / 100f
        paint.color = COLOR_BASAL
        rect.set(0f, 0f, split, h)
        canvas.drawRect(rect, paint)

        paint.color = COLOR_BOLUS
        rect.set(split, 0f, w, h)
        canvas.drawRect(rect, paint)

        canvas.restore()
    }
}
