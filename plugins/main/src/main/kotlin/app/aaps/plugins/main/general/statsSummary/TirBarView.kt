package app.aaps.plugins.main.general.statsSummary

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * Community patch — barre horizontale à trois segments (bas / en cible / haut).
 *
 * Purement décorative : n'affiche que des valeurs qu'on lui passe.
 */
class TirBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {

        const val COLOR_LOW = 0xFFFF5252.toInt()
        const val COLOR_IN_RANGE = 0xFF00E676.toInt()
        const val COLOR_HIGH = 0xFFFFEB3B.toInt()
        private const val COLOR_EMPTY = 0x33888888
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    private var percentLow = 0
    private var percentInRange = 0
    private var percentHigh = 0

    fun setValues(low: Int, inRange: Int, high: Int) {
        percentLow = low
        percentInRange = inRange
        percentHigh = high
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val radius = h / 3f

        val total = percentLow + percentInRange + percentHigh
        if (total <= 0) {
            paint.color = COLOR_EMPTY
            rect.set(0f, 0f, w, h)
            canvas.drawRoundRect(rect, radius, radius, paint)
            return
        }

        // Arrondis aux extrémités seulement : on dessine la barre complète
        // arrondie, puis les segments par-dessus en la découpant.
        canvas.save()
        rect.set(0f, 0f, w, h)
        val clip = android.graphics.Path()
        clip.addRoundRect(rect, radius, radius, android.graphics.Path.Direction.CW)
        canvas.clipPath(clip)

        var x = 0f
        listOf(
            percentLow to COLOR_LOW,
            percentInRange to COLOR_IN_RANGE,
            percentHigh to COLOR_HIGH
        ).forEach { (value, color) ->
            if (value > 0) {
                val segment = w * value / total
                paint.color = color
                rect.set(x, 0f, x + segment, h)
                canvas.drawRect(rect, paint)
                x += segment
            }
        }
        canvas.restore()
    }
}
