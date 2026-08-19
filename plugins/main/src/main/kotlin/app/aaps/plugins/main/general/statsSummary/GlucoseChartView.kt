package app.aaps.plugins.main.general.statsSummary

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.max
import kotlin.math.min

/**
 * Community patch — graphique de l'onglet Stats.
 *
 * Deux modes selon la période affichée :
 *  - 24 h  : courbe brute de toutes les valeurs de la journée
 *  - 7/30j : profil moyen par heure (médiane + zone interquartile)
 *
 * Purement décorative : n'affiche que ce qu'on lui passe.
 */
class GlucoseChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {

        private const val COLOR_LINE = 0xFF00E676.toInt()
        private const val COLOR_BAND = 0x382E7D32
        private const val COLOR_IQR = 0x3300E676
        private const val COLOR_GRID = 0x33FFFFFF
        private const val COLOR_LABEL = 0xB0FFFFFF.toInt()

        private const val PADDING_RIGHT_DP = 30f
        private const val PADDING_BOTTOM_DP = 16f
        private const val PADDING_TOP_DP = 6f
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val density = resources.displayMetrics.density

    private var lowMark = 72.0
    private var highMark = 180.0
    private var decimals = 0

    /** Mode 24 h : abscisse = temps réel entre startTime et endTime. */
    private var rawPoints: List<StatsSummaryCalculator.RawPoint> = emptyList()
    private var startTime = 0L
    private var endTime = 0L

    /** Mode 7/30 j : abscisse = heure de la journée 0-24. */
    private var hourly: List<StatsSummaryCalculator.HourlyPoint> = emptyList()

    fun setRange(low: Double, high: Double, decimals: Int) {
        lowMark = low
        highMark = high
        this.decimals = decimals
    }

    fun showRaw(points: List<StatsSummaryCalculator.RawPoint>, from: Long, to: Long) {
        rawPoints = points
        hourly = emptyList()
        startTime = from
        endTime = to
        invalidate()
    }

    fun showHourly(profile: List<StatsSummaryCalculator.HourlyPoint>) {
        hourly = profile
        rawPoints = emptyList()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val values = when {
            rawPoints.isNotEmpty() -> rawPoints.map { it.value }
            hourly.isNotEmpty()    -> hourly.flatMap { listOf(it.p25, it.p75) }
            else                   -> return
        }

        val padRight = PADDING_RIGHT_DP * density
        val padBottom = PADDING_BOTTOM_DP * density
        val padTop = PADDING_TOP_DP * density
        val plotLeft = 0f
        val plotRight = width - padRight
        val plotTop = padTop
        val plotBottom = height - padBottom
        val plotW = plotRight - plotLeft
        val plotH = plotBottom - plotTop
        if (plotW <= 0 || plotH <= 0) return

        // L'échelle englobe toujours la plage cible, plus une marge
        var minV = min(values.min(), lowMark)
        var maxV = max(values.max(), highMark)
        val span = (maxV - minV).takeIf { it > 0 } ?: 1.0
        minV -= span * 0.10
        maxV += span * 0.10

        fun yOf(v: Double) = (plotBottom - plotH * ((v - minV) / (maxV - minV))).toFloat()

        // --- Bande de plage cible ---
        paint.style = Paint.Style.FILL
        paint.color = COLOR_BAND
        canvas.drawRect(plotLeft, yOf(highMark), plotRight, yOf(lowMark), paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f * density
        paint.color = COLOR_GRID
        canvas.drawLine(plotLeft, yOf(lowMark), plotRight, yOf(lowMark), paint)
        canvas.drawLine(plotLeft, yOf(highMark), plotRight, yOf(highMark), paint)

        paint.style = Paint.Style.FILL
        paint.color = COLOR_LABEL
        paint.textSize = 9f * density
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText(format(highMark), plotRight + 3f * density, yOf(highMark) + 3f * density, paint)
        canvas.drawText(format(lowMark), plotRight + 3f * density, yOf(lowMark) + 3f * density, paint)

        if (rawPoints.isNotEmpty()) drawRaw(canvas, plotLeft, plotRight, plotBottom, ::yOf)
        else drawHourly(canvas, plotLeft, plotW, plotBottom, ::yOf)
    }

    private fun drawRaw(canvas: Canvas, left: Float, right: Float, bottom: Float, yOf: (Double) -> Float) {
        val span = (endTime - startTime).toFloat().takeIf { it > 0 } ?: return
        fun xOf(t: Long) = left + (right - left) * ((t - startTime).toFloat() / span)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * density
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = COLOR_LINE
        val path = Path()
        rawPoints.forEachIndexed { i, p ->
            val x = xOf(p.timestamp)
            val y = yOf(p.value)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, paint)

        // Repères toutes les 6 h
        drawHourLabels(canvas, left, right - left, bottom) { hour ->
            // Position de l'heure ronde dans la fenêtre affichée
            val cal = java.util.Calendar.getInstance()
            cal.timeInMillis = startTime
            cal.set(java.util.Calendar.HOUR_OF_DAY, hour)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            val t = cal.timeInMillis
            if (t in startTime..endTime) xOf(t) else null
        }
    }

    private fun drawHourly(canvas: Canvas, left: Float, plotW: Float, bottom: Float, yOf: (Double) -> Float) {
        fun xOf(hour: Double) = left + plotW * (hour / 24.0).toFloat()

        // Zone interquartile
        paint.style = Paint.Style.FILL
        paint.color = COLOR_IQR
        val band = Path()
        hourly.forEachIndexed { i, p ->
            val x = xOf(p.hour + 0.5)
            if (i == 0) band.moveTo(x, yOf(p.p75)) else band.lineTo(x, yOf(p.p75))
        }
        hourly.reversed().forEach { p ->
            band.lineTo(xOf(p.hour + 0.5), yOf(p.p25))
        }
        band.close()
        canvas.drawPath(band, paint)

        // Médiane
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f * density
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = COLOR_LINE
        val median = Path()
        hourly.forEachIndexed { i, p ->
            val x = xOf(p.hour + 0.5)
            val y = yOf(p.median)
            if (i == 0) median.moveTo(x, y) else median.lineTo(x, y)
        }
        canvas.drawPath(median, paint)

        drawHourLabels(canvas, left, plotW, bottom) { hour -> xOf(hour.toDouble()) }
    }

    private fun drawHourLabels(canvas: Canvas, left: Float, plotW: Float, bottom: Float, xForHour: (Int) -> Float?) {
        paint.textSize = 9f * density
        paint.textAlign = Paint.Align.CENTER
        listOf(0, 6, 12, 18, 24).forEach { hour ->
            val x = xForHour(if (hour == 24) 23 else hour) ?: return@forEach
            val px = if (hour == 24) left + plotW else x
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f * density
            paint.color = COLOR_GRID
            if (hour != 0 && hour != 24) canvas.drawLine(px, bottom, px, 0f, paint)
            paint.style = Paint.Style.FILL
            paint.color = COLOR_LABEL
            canvas.drawText("${hour}h", px, bottom + 11f * density, paint)
        }
    }

    private fun format(v: Double): String =
        if (decimals > 0) String.format("%.1f", v) else String.format("%.0f", v)
}
