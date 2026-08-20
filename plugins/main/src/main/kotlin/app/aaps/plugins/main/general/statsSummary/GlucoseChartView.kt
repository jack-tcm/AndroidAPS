package app.aaps.plugins.main.general.statsSummary

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
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
        private const val COLOR_LINE_HIGH = 0xFFFFEB3B.toInt()
        private const val COLOR_LINE_LOW = 0xFFFF5252.toInt()
        private const val COLOR_BAND = 0x382E7D32
        private const val COLOR_IQR = 0x3300E676
        private const val COLOR_GRID = 0x33FFFFFF
        private const val COLOR_LABEL = 0xB0FFFFFF.toInt()

        private const val PADDING_RIGHT_DP = 30f
        private const val PADDING_BOTTOM_DP = 16f
        private const val PADDING_TOP_DP = 6f
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
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

        if (rawPoints.isNotEmpty()) drawRaw(canvas, plotLeft, plotRight, plotBottom) { v -> yOf(v) }
        else drawHourly(canvas, plotLeft, plotW, plotBottom) { v -> yOf(v) }
    }

    private fun drawRaw(canvas: Canvas, left: Float, right: Float, bottom: Float, yOf: (Double) -> Float) {
        val span = (endTime - startTime).toFloat().takeIf { it > 0 } ?: return
        fun xOf(t: Long) = left + (right - left) * ((t - startTime).toFloat() / span)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * density
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeCap = Paint.Cap.ROUND
        drawColoredLine(canvas, rawPoints.map { xOf(it.timestamp) to it.value }, yOf)

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
        drawColoredLine(canvas, hourly.map { xOf(it.hour + 0.5) to it.median }, yOf)

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

    /**
     * Trace une polyligne dont la couleur suit la valeur : vert dans la
     * cible, jaune au-dessus du repère haut, rouge en dessous du repère bas.
     *
     * Chaque segment est découpé exactement au point de franchissement d'une
     * borne, pour que le changement de couleur tombe pile sur la ligne de
     * repère et non au point de mesure suivant.
     *
     * @param pts liste de (x en pixels, valeur dans l'unité d'affichage)
     */
    private fun drawColoredLine(canvas: Canvas, pts: List<Pair<Float, Double>>, yOf: (Double) -> Float) {
        if (pts.size < 2) return

        for (i in 1 until pts.size) {
            val (x0, v0) = pts[i - 1]
            val (x1, v1) = pts[i]

            // Fractions du segment où une borne est franchie
            val cuts = sortedSetOf(0.0, 1.0)
            listOf(lowMark, highMark).forEach { threshold ->
                if ((v0 - threshold) * (v1 - threshold) < 0.0 && v1 != v0) {
                    cuts.add((threshold - v0) / (v1 - v0))
                }
            }

            val ts = cuts.toList()
            for (j in 1 until ts.size) {
                val ta = ts[j - 1]
                val tb = ts[j]
                val va = v0 + (v1 - v0) * ta
                val vb = v0 + (v1 - v0) * tb
                // La couleur est décidée au milieu du sous-segment : il est
                // par construction entièrement d'un seul côté des bornes.
                paint.color = colorFor((va + vb) / 2.0)
                canvas.drawLine(
                    x0 + (x1 - x0) * ta.toFloat(), yOf(va),
                    x0 + (x1 - x0) * tb.toFloat(), yOf(vb),
                    paint
                )
            }
        }
    }

    private fun colorFor(value: Double): Int = when {
        value > highMark -> COLOR_LINE_HIGH
        value < lowMark  -> COLOR_LINE_LOW
        else             -> COLOR_LINE
    }

    private fun format(v: Double): String =
        if (decimals > 0) String.format("%.1f", v) else String.format("%.0f", v)
}
