package app.aaps.plugins.main.general.statsSummary

import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.stats.TddCalculator
import app.aaps.core.keys.IntKey
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
     * Un point du profil glycémique moyen, pour une heure de la journée.
     * Valeurs dans l'unité d'affichage de l'utilisateur.
     */
    data class HourlyPoint(
        val hour: Int,
        val median: Double,
        val p25: Double,
        val p75: Double
    )

    /** Une valeur brute pour le graphique du mode 24 h. */
    data class RawPoint(val timestamp: Long, val value: Double)

    /** Temps en cible sur une tranche horaire. */
    data class SlotStat(
        val startHour: Int,
        val endHour: Int,
        val percentLow: Int,
        val percentInRange: Int,
        val percentHigh: Int,
        val hasData: Boolean
    )

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
        /** Proportion du temps couverte par des mesures, null si indéterminable. */
        val coveragePercent: Int?,
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
        val isPerDay: Boolean,
        /** Valeurs brutes — renseigné en mode 24 h uniquement. */
        val rawPoints: List<RawPoint>,
        /** Profil moyen par heure — renseigné en 7 j / 30 j uniquement. */
        val hourlyProfile: List<HourlyPoint>,
        /** Temps en cible par tranche horaire configurable. */
        val slots: List<SlotStat>
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

        // Couverture : on additionne les intervalles entre mesures
        // consécutives, chacun plafonné à 10 min, pour ne pas dépendre de la
        // cadence du capteur (1 min avec Juggluco, 5 min avec un Dexcom) et
        // pour qu'un long trou ne compte pas comme du temps couvert.
        val coverage = if (count >= 2 && end > start) {
            var covered = 0L
            for (i in 1 until readings.size) {
                covered += (readings[i].timestamp - readings[i - 1].timestamp).coerceAtMost(600_000L)
            }
            (covered * 100.0 / (end - start)).roundToInt().coerceIn(0, 100)
        } else null

        // --- Graphique : valeurs brutes en 24 h, profil moyen au-delà ---
        val rawPoints =
            if (period == Period.DAY)
                smooth(readings.map { RawPoint(it.timestamp, profileUtil.fromMgdlToUnits(it.value)) })
            else emptyList()

        val hourlyProfile =
            if (period == Period.DAY) emptyList()
            else buildHourlyProfile(readings)

        // --- Temps en cible par tranche horaire ---
        val slots = buildSlots(readings, lowMark, highMark)

        return Result(
            startTime = start,
            endTime = end,
            period = period,
            offset = offset,
            readingCount = count,
            coveragePercent = coverage,
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
            isPerDay = perDay,
            rawPoints = rawPoints,
            hourlyProfile = hourlyProfile,
            slots = slots
        )
    }

    /**
     * Lissage du graphique 24 h : regroupe les valeurs par tranches de N
     * minutes et ne garde que la médiane de chaque tranche.
     *
     * Combine sous-échantillonnage (rendu identique quelle que soit la
     * cadence de la source — 1 min avec Juggluco, 5 min avec un Dexcom) et
     * médiane plutôt que moyenne, pour effacer le bruit de mesure sans
     * écraser les vrais pics.
     *
     * N = 0 désactive le lissage et renvoie toutes les valeurs brutes.
     */
    private fun smooth(points: List<RawPoint>): List<RawPoint> {
        val minutes = preferences.get(IntKey.StatsSummarySmoothingMinutes)
        if (minutes <= 0 || points.size < 3) return points

        val bucketMs = minutes * 60_000L
        val lastTimestamp = points.last().timestamp
        return points
            .groupBy { it.timestamp / bucketMs }
            .toSortedMap()
            .map { (bucket, values) ->
                val sorted = values.map { it.value }.sorted()
                RawPoint(
                    // Milieu de la tranche : évite que la courbe se décale.
                    // Plafonné à la dernière mesure réelle, pour que la
                    // tranche en cours (incomplète) ne déborde pas à droite.
                    timestamp = (bucket * bucketMs + bucketMs / 2).coerceAtMost(lastTimestamp),
                    value = percentile(sorted, 0.50)
                )
            }
    }

    /**
     * Regroupe les valeurs par heure de la journée (0-23) et calcule pour
     * chacune la médiane et les quartiles. Une heure sans donnée est omise.
     */
    private fun buildHourlyProfile(readings: List<app.aaps.core.data.model.GV>): List<HourlyPoint> {
        val buckets = Array(24) { mutableListOf<Double>() }
        val cal = Calendar.getInstance()
        readings.forEach { gv ->
            cal.timeInMillis = gv.timestamp
            buckets[cal.get(Calendar.HOUR_OF_DAY)].add(profileUtil.fromMgdlToUnits(gv.value))
        }
        return buckets.mapIndexedNotNull { hour, values ->
            if (values.isEmpty()) null
            else {
                val sorted = values.sorted()
                HourlyPoint(
                    hour = hour,
                    median = percentile(sorted, 0.50),
                    p25 = percentile(sorted, 0.25),
                    p75 = percentile(sorted, 0.75)
                )
            }
        }
    }

    /** Percentile par interpolation linéaire sur une liste déjà triée. */
    private fun percentile(sorted: List<Double>, fraction: Double): Double {
        if (sorted.isEmpty()) return 0.0
        if (sorted.size == 1) return sorted[0]
        val pos = fraction * (sorted.size - 1)
        val lower = pos.toInt()
        val upper = (lower + 1).coerceAtMost(sorted.size - 1)
        val weight = pos - lower
        return sorted[lower] * (1 - weight) + sorted[upper] * weight
    }

    /**
     * Répartit les valeurs dans les quatre tranches horaires configurées
     * (Préférences → Statistiques) et calcule le temps en cible de chacune.
     *
     * Les bornes sont triées et dédoublonnées : une tranche peut donc
     * enjamber minuit (ex. 23 h → 02 h), et un réglage incohérent ne casse
     * rien, il produit simplement moins de tranches.
     */
    private fun buildSlots(
        readings: List<app.aaps.core.data.model.GV>,
        lowMark: Double,
        highMark: Double
    ): List<SlotStat> {
        val starts = listOf(
            preferences.get(IntKey.StatsSummarySlot1Start),
            preferences.get(IntKey.StatsSummarySlot2Start),
            preferences.get(IntKey.StatsSummarySlot3Start),
            preferences.get(IntKey.StatsSummarySlot4Start)
        ).map { it.coerceIn(0, 23) }.distinct().sorted()

        if (starts.isEmpty()) return emptyList()

        // Chaque tranche va de sa borne à la borne suivante (la dernière
        // enjambe minuit pour rejoindre la première).
        val ranges = starts.mapIndexed { i, start ->
            start to starts[(i + 1) % starts.size]
        }

        val cal = Calendar.getInstance()
        val counters = ranges.map { intArrayOf(0, 0, 0) } // bas, cible, haut

        readings.forEach { gv ->
            cal.timeInMillis = gv.timestamp
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val index = ranges.indexOfFirst { (start, end) ->
                if (start <= end) hour >= start && hour < end
                else hour >= start || hour < end // tranche à cheval sur minuit
            }
            if (index >= 0) {
                val v = profileUtil.fromMgdlToUnits(gv.value)
                when {
                    v < lowMark  -> counters[index][0]++
                    v > highMark -> counters[index][2]++
                    else         -> counters[index][1]++
                }
            }
        }

        return ranges.mapIndexed { i, (start, end) ->
            val c = counters[i]
            val total = c[0] + c[1] + c[2]
            if (total == 0) {
                SlotStat(start, end, 0, 0, 0, hasData = false)
            } else {
                val pLow = (c[0] * 100.0 / total).roundToInt()
                val pHigh = (c[2] * 100.0 / total).roundToInt()
                SlotStat(
                    startHour = start,
                    endHour = end,
                    percentLow = pLow,
                    percentInRange = (100 - pLow - pHigh).coerceAtLeast(0),
                    percentHigh = pHigh,
                    hasData = true
                )
            }
        }
    }
}
