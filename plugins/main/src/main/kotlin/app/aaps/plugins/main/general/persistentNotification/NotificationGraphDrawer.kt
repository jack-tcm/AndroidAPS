package app.aaps.plugins.main.general.persistentNotification

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import app.aaps.core.data.model.GV
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

/**
 * Community patch — dessine un graphique des dernières heures de glycémie,
 * destiné à être affiché dans la notification persistante quand elle est dépliée
 * (via NotificationCompat.BigPictureStyle).
 *
 * Purement de l'affichage : ne modifie aucune donnée, aucun calcul de dose,
 * aucune contrainte de sécurité. Lit simplement la base locale en lecture seule.
 */
@Singleton
class NotificationGraphDrawer @Inject constructor(
    private val persistenceLayer: PersistenceLayer,
    private val profileFunction: ProfileFunction,
    private val profileUtil: ProfileUtil
) {

    companion object {

        /** Fenêtre de temps affichée dans le graphique. */
        const val HOURS_TO_SHOW = 3

        /** Dimensions du bitmap. Ratio large adapté à la zone BigPicture d'Android. */
        private const val WIDTH = 1024
        private const val HEIGHT = 384

        private const val PADDING_LEFT = 12f
        private const val PADDING_RIGHT = 96f   // place pour les libellés d'axe à droite
        private const val PADDING_TOP = 24f
        private const val PADDING_BOTTOM = 40f  // place pour les libellés horaires

        private const val COLOR_IN_RANGE = 0xFF4CAF50.toInt()
        private const val COLOR_HIGH = 0xFFFFB300.toInt()
        private const val COLOR_LOW = 0xFFE53935.toInt()
        private const val COLOR_GRID = 0x40FFFFFF
        private const val COLOR_LABEL = 0xB0FFFFFF.toInt()
        private const val COLOR_RANGE_BAND = 0x1A4CAF50
    }

    /**
     * Construit le bitmap du graphique, ou null s'il n'y a pas assez de données
     * (dans ce cas l'appelant n'ajoute simplement pas de BigPictureStyle et la
     * notification garde exactement son comportement d'origine).
     */
    fun draw(): Bitmap? {
        val now = System.currentTimeMillis()
        val from = now - HOURS_TO_SHOW * 60 * 60 * 1000L

        val readings: List<GV> = try {
            persistenceLayer.getBgReadingsDataFromTimeToTime(from, now, true)
        } catch (e: Exception) {
            return null
        }
        if (readings.size < 2) return null

        // Bornes de l'axe Y, en unités d'affichage de l'utilisateur (mg/dl ou mmol/l)
        val values = readings.map { profileUtil.fromMgdlToUnits(it.value) }
        var minV = values.min()
        var maxV = values.max()

        val profile = profileFunction.getProfile()
        val lowLine = profile?.let { profileUtil.fromMgdlToUnits(it.getTargetLowMgdl()) }
        val highLine = profile?.let { profileUtil.fromMgdlToUnits(it.getTargetHighMgdl()) }

        // On élargit pour englober la plage cible, puis on ajoute une marge
        lowLine?.let { minV = min(minV, it) }
        highLine?.let { maxV = max(maxV, it) }
        val span = (maxV - minV).takeIf { it > 0.0 } ?: 1.0
        minV -= span * 0.12
        maxV += span * 0.12

        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT)

        val plotLeft = PADDING_LEFT
        val plotRight = WIDTH - PADDING_RIGHT
        val plotTop = PADDING_TOP
        val plotBottom = HEIGHT - PADDING_BOTTOM
        val plotWidth = plotRight - plotLeft
        val plotHeight = plotBottom - plotTop

        fun xOf(t: Long): Float =
            plotLeft + plotWidth * ((t - from).toFloat() / (now - from).toFloat())

        fun yOf(v: Double): Float =
            (plotBottom - plotHeight * ((v - minV) / (maxV - minV))).toFloat()

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // --- Bande de plage cible ---
        if (lowLine != null && highLine != null) {
            paint.style = Paint.Style.FILL
            paint.color = COLOR_RANGE_BAND
            canvas.drawRect(plotLeft, yOf(highLine), plotRight, yOf(lowLine), paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            paint.color = COLOR_GRID
            canvas.drawLine(plotLeft, yOf(lowLine), plotRight, yOf(lowLine), paint)
            canvas.drawLine(plotLeft, yOf(highLine), plotRight, yOf(highLine), paint)

            // Libellés des bornes de plage, à droite
            paint.style = Paint.Style.FILL
            paint.color = COLOR_LABEL
            paint.textSize = 26f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText(formatValue(highLine), plotRight + 10f, yOf(highLine) + 9f, paint)
            canvas.drawText(formatValue(lowLine), plotRight + 10f, yOf(lowLine) + 9f, paint)
        }

        // --- Repères horaires verticaux (une ligne par heure) ---
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = COLOR_GRID
        paint.pathEffect = null
        for (h in 1 until HOURS_TO_SHOW) {
            val t = now - h * 60 * 60 * 1000L
            val x = xOf(t)
            canvas.drawLine(x, plotTop, x, plotBottom, paint)
        }

        paint.style = Paint.Style.FILL
        paint.color = COLOR_LABEL
        paint.textSize = 24f
        paint.textAlign = Paint.Align.CENTER
        for (h in 1 until HOURS_TO_SHOW) {
            val x = xOf(now - h * 60 * 60 * 1000L)
            canvas.drawText("-${h}h", x, plotBottom + 30f, paint)
        }
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("maintenant", plotRight, plotBottom + 30f, paint)

        // --- Courbe ---
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 5f
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = COLOR_IN_RANGE
        val path = Path()
        readings.forEachIndexed { i, gv ->
            val x = xOf(gv.timestamp)
            val y = yOf(profileUtil.fromMgdlToUnits(gv.value))
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, paint)

        // --- Points, colorés selon la plage cible ---
        paint.style = Paint.Style.FILL
        readings.forEach { gv ->
            val v = profileUtil.fromMgdlToUnits(gv.value)
            paint.color = when {
                highLine != null && v > highLine -> COLOR_HIGH
                lowLine != null && v < lowLine   -> COLOR_LOW
                else                             -> COLOR_IN_RANGE
            }
            canvas.drawCircle(xOf(gv.timestamp), yOf(v), 5f, paint)
        }

        // --- Dernière valeur mise en évidence ---
        readings.lastOrNull()?.let { last ->
            val v = profileUtil.fromMgdlToUnits(last.value)
            val x = xOf(last.timestamp)
            val y = yOf(v)
            paint.color = when {
                highLine != null && v > highLine -> COLOR_HIGH
                lowLine != null && v < lowLine   -> COLOR_LOW
                else                             -> COLOR_IN_RANGE
            }
            canvas.drawCircle(x, y, 11f, paint)

            paint.textSize = 44f
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(formatValue(v), plotRight - 6f, plotTop + 40f, paint)
        }

        return bitmap
    }

    private fun formatValue(v: Double): String =
        if (profileUtil.units == app.aaps.core.data.model.GlucoseUnit.MMOL)
            String.format("%.1f", v)
        else
            String.format("%.0f", v)
}
