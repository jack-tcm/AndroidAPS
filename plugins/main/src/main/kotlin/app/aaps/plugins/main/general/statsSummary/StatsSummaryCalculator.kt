package app.aaps.plugins.main.general.statsSummary

import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.stats.TddCalculator
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.Preferences
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Community patch — calcule les indicateurs de l'onglet Stats.
 *
 * Strictement en lecture seule : n'écrit rien en base, ne touche ni à la
 * boucle, ni au dosage, ni à aucune contrainte de sécurité.
 */
@Singleton
class StatsSummaryCalculator @Inject constructor(
    private val persistenceLayer: PersistenceLayer,
    private val tddCalculator: TddCalculator,
    private val preferences: Preferences,
    private val profileUtil: ProfileUtil
) {

    enum class Period(val days: Int) {
        DAY(1), WEEK(7), MONTH(30)
    }

    /**
     * Indicateurs calculés sur une période. Les champs nullables valent null
     * quand la donnée n'est pas disponible (aucune lecture capteur sur la
     * période, TDD non calculable).
     */
    data class Result(
        val startTime: Long,
        val endTime: Long,
        val period: Period,
        val offset: Int,
        val readingCount: Int,
        val percentLow: Int,
        val percentInRange: Int,
        val percentHigh: Int,
        /** Moyenne, dans l'unité d'affichage de l'utilisateur. */
        val average: Double?,
        val estimatedA1cPercent: Double?,
        /** Repères bas/haut, dans l'unité d'affichage. */
        val lowMark: Double,
        val highMark: Double,
        /** Cumul sur 24 h, moyenne par jour au-delà (voir [isPerDay]). */
        val totalInsulin: Double?,
        val basalInsulin: Double?,
        val bolusInsulin: Double?,
        val carbs: Double?,
        val isPerDay: Boolean
    ) {

        val basalPercent: Int?
            get() {
                val total = totalInsulin ?: return null
                val basal = basalInsulin ?: return null
                if (total <= 0.0) return null
                return (basal / total * 100.0).roundToInt().coerceIn(0, 100)
            }

        val bolusPercent: Int?
            get() = basalPercent?.let { 100 - it }
    }

    /**
     * Bornes [début, fin] de la période demandée, en jours calendaires.
     *
     * Le début est toujours minuit. La fin est minuit du lendemain de la
     * période, plafonnée à l'instant présent pour la période en cours
     * (une journée en cours est donc partielle, ce qui est voulu).
     *
     * @param offset 0 = période la plus récente, 1 = la précédente, etc.
     */
    fun boundsFor(period: Period, offset: Int): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        // Minuit du premier jour de la période la plus récente...
        cal.add(Calendar.DAY_OF_YEAR, -(period.days - 1))
        // ...puis on recule de 'offset' périodes entières.
        cal.add(Calendar.DAY_OF_YEAR, -offset * period.days)
        val start = cal.timeInMillis

        cal.add(Calendar.DAY_OF_YEAR, period.days)
        val end = minOf(cal.timeInMillis, System.currentTimeMillis())

        return start to end
    }

    fun calculate(period: Period, offset: Int): Result {
        val (start, end) = boundsFor(period, offset)

        // Mêmes repères que l'écran principal (Préférences → Overview).
        // Preferences.get(UnitDoublePreferenceKey) renvoie déjà la valeur
        // convertie dans l'unité d'affichage de l'utilisateur.
        val lowMark = preferences.get(UnitDoubleKey.OverviewLowMark)
        val highMark = preferences.get(UnitDoubleKey.OverviewHighMark)

        val readings = try {
            persistenceLayer.getBgReadingsDataFromTimeToTime(start, end, true)
        } catch (e: Exception) {
            emptyList()
        }

        var low = 0
        var high = 0
        var sumMgdl = 0.0
        readings.forEach { gv ->
            val v = profileUtil.fromMgdlToUnits(gv.value)
            when {
                v < lowMark  -> low++
                v > highMark -> high++
            }
            sumMgdl += gv.value
        }
        val count = readings.size
        val avgMgdl = if (count > 0) sumMgdl / count else null

        // Les bas et les hauts sont arrondis, la cible prend le reste :
        // le total fait ainsi toujours exactement 100 %.
        var pLow = 0
        var pHigh = 0
        var pInRange = 0
        if (count > 0) {
            pLow = (low * 100.0 / count).roundToInt()
            pHigh = (high * 100.0 / count).roundToInt()
            pInRange = (100 - pLow - pHigh).coerceAtLeast(0)
        }

        // HbA1c estimée — formule ADAG, à partir de la moyenne en mg/dl
        val estimatedA1c = avgMgdl?.let { (it + 46.7) / 28.7 }

        val tdd = try {
            tddCalculator.calculateInterval(start, end, true)
        } catch (e: Exception) {
            null
        }

        // Cumul sur 24 h, moyenne journalière au-delà.
        val perDay = period != Period.DAY
        val daysCovered = ceil((end - start) / 86_400_000.0).toInt().coerceAtLeast(1)
        val divisor = if (perDay) daysCovered.toDouble() else 1.0

        // totalAmount vaut 0 quand il n'a pas été stocké : on le recompose.
        val total = tdd?.let {
            if (it.totalAmount > 0.0) it.totalAmount else it.basalAmount + it.bolusAmount
        }

        return Result(
            startTime = start,
            endTime = end,
            period = period,
            offset = offset,
            readingCount = count,
            percentLow = pLow,
            percentInRange = pInRange,
            percentHigh = pHigh,
            average = avgMgdl?.let { profileUtil.fromMgdlToUnits(it) },
            estimatedA1cPercent = estimatedA1c,
            lowMark = lowMark,
            highMark = highMark,
            totalInsulin = total?.div(divisor),
            basalInsulin = tdd?.basalAmount?.div(divisor),
            bolusInsulin = tdd?.bolusAmount?.div(divisor),
            carbs = tdd?.carbs?.div(divisor),
            isPerDay = perDay
        )
    }
}
