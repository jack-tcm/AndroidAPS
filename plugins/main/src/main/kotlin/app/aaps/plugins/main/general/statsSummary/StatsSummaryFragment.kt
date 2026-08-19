package app.aaps.plugins.main.general.statsSummary

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.plugins.main.R
import app.aaps.plugins.main.databinding.StatssummaryFragmentBinding
import app.aaps.plugins.main.databinding.StatssummarySlotRowBinding
import com.google.android.material.tabs.TabLayout
import dagger.android.support.DaggerFragment
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * Community patch — écran de l'onglet Stats.
 *
 * Affiche des indicateurs en lecture seule sur 24 h, 7 jours ou 30 jours,
 * avec navigation vers les périodes précédentes. Aucune action sur la boucle.
 */
class StatsSummaryFragment : DaggerFragment() {

    @Inject lateinit var calculator: StatsSummaryCalculator
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var profileUtil: ProfileUtil

    private val disposable = CompositeDisposable()

    private var period = StatsSummaryCalculator.Period.DAY
    private var offset = 0

    private var _binding: StatssummaryFragmentBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        StatssummaryFragmentBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.statssummaryPeriodTabs.apply {
            addTab(newTab().setText(rh.gs(R.string.statssummary_period_day)))
            addTab(newTab().setText(rh.gs(R.string.statssummary_period_week)))
            addTab(newTab().setText(rh.gs(R.string.statssummary_period_month)))
            addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) {
                    period = when (tab.position) {
                        1    -> StatsSummaryCalculator.Period.WEEK
                        2    -> StatsSummaryCalculator.Period.MONTH
                        else -> StatsSummaryCalculator.Period.DAY
                    }
                    // Revenir à la période la plus récente en changeant d'échelle
                    offset = 0
                    refresh()
                }

                override fun onTabUnselected(tab: TabLayout.Tab) {}
                override fun onTabReselected(tab: TabLayout.Tab) {}
            })
        }

        binding.statssummaryPrev.setOnClickListener {
            offset++
            refresh()
        }
        binding.statssummaryNext.setOnClickListener {
            if (offset > 0) {
                offset--
                refresh()
            }
        }

        refresh()
    }

    override fun onDestroyView() {
        disposable.clear()
        super.onDestroyView()
        _binding = null
    }

    private fun refresh() {
        // La flèche "suivant" est inutile sur la période la plus récente
        binding.statssummaryNext.isEnabled = offset > 0
        binding.statssummaryNext.alpha = if (offset > 0) 1.0f else 0.3f

        val requestedPeriod = period
        val requestedOffset = offset

        disposable += Single.fromCallable { calculator.calculate(requestedPeriod, requestedOffset) }
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.main)
            .subscribe(
                { result ->
                    // La vue peut avoir été détruite pendant le calcul
                    if (_binding == null) return@subscribe
                    // Un autre onglet a pu être choisi entre-temps
                    if (result.period != period || result.offset != offset) return@subscribe
                    display(result)
                },
                { error -> aapsLogger.error("StatsSummary calculation failed", error) }
            )
    }

    private fun display(r: StatsSummaryCalculator.Result) {
        binding.statssummaryPeriodLabel.text = periodLabel(r)
        binding.statssummaryPeriodSublabel.text = periodSublabel(r)

        binding.statssummaryTirBar.setValues(r.percentLow, r.percentInRange, r.percentHigh)
        binding.statssummaryTirLow.text = rh.gs(R.string.statssummary_tir_low, r.percentLow)
        binding.statssummaryTirInrange.text = rh.gs(R.string.statssummary_tir_inrange, r.percentInRange)
        binding.statssummaryTirHigh.text = rh.gs(R.string.statssummary_tir_high, r.percentHigh)
        binding.statssummaryTirLow.setTextColor(TirBarView.COLOR_LOW)
        binding.statssummaryTirInrange.setTextColor(TirBarView.COLOR_IN_RANGE)
        binding.statssummaryTirHigh.setTextColor(TirBarView.COLOR_HIGH)

        binding.statssummaryCoverage.text = r.coveragePercent?.let {
            rh.gs(R.string.statssummary_coverage, r.readingCount, it)
        } ?: rh.gs(R.string.statssummary_coverage_readings_only, r.readingCount)

        binding.statssummaryAverage.text = r.average?.let { formatGlucose(it) } ?: PLACEHOLDER
        binding.statssummaryA1c.text = r.estimatedA1cPercent?.let { String.format(Locale.getDefault(), "%.1f %%", it) } ?: PLACEHOLDER

        // Cumul sur 24 h, moyenne par jour au-delà
        val insulinLabel = if (r.isPerDay) R.string.statssummary_insulin_per_day else R.string.statssummary_insulin_total
        val carbsLabel = if (r.isPerDay) R.string.statssummary_carbs_per_day else R.string.statssummary_carbs_total
        binding.statssummaryInsulinLabel.text = rh.gs(insulinLabel)
        binding.statssummaryCarbsLabel.text = rh.gs(carbsLabel)

        binding.statssummaryInsulin.text =
            r.totalInsulin?.let { String.format(Locale.getDefault(), "%.1f U", it) } ?: PLACEHOLDER
        binding.statssummaryCarbs.text =
            r.carbs?.let { String.format(Locale.getDefault(), "%.0f g", it) } ?: PLACEHOLDER

        val basalPercent = r.basalPercent
        binding.statssummarySplitBar.setValue(basalPercent)
        if (basalPercent != null && r.basalInsulin != null && r.bolusInsulin != null) {
            binding.statssummaryBasalLabel.text =
                rh.gs(R.string.statssummary_basal_share, basalPercent, r.basalInsulin)
            binding.statssummaryBolusLabel.text =
                rh.gs(R.string.statssummary_bolus_share, r.bolusPercent ?: 0, r.bolusInsulin)
        } else {
            binding.statssummaryBasalLabel.text = rh.gs(R.string.statssummary_basal)
            binding.statssummaryBolusLabel.text = PLACEHOLDER
        }
        binding.statssummaryBasalLabel.setTextColor(SplitBarView.COLOR_BASAL)
        binding.statssummaryBolusLabel.setTextColor(SplitBarView.COLOR_BOLUS)

        displayChart(r)
        displaySlots(r)
    }

    private fun displayChart(r: StatsSummaryCalculator.Result) {
        val decimals = if (profileUtil.units == app.aaps.core.data.model.GlucoseUnit.MMOL) 1 else 0
        binding.statssummaryChart.setRange(r.lowMark, r.highMark, decimals)

        if (r.period == StatsSummaryCalculator.Period.DAY) {
            binding.statssummaryChartTitle.text = rh.gs(R.string.statssummary_chart_day)
            binding.statssummaryChartSubtitle.text = rh.gs(R.string.statssummary_chart_day_sub)
            binding.statssummaryChart.showRaw(r.rawPoints, r.startTime, r.endTime)
        } else {
            binding.statssummaryChartTitle.text = rh.gs(R.string.statssummary_chart_profile)
            binding.statssummaryChartSubtitle.text = rh.gs(R.string.statssummary_chart_profile_sub)
            binding.statssummaryChart.showHourly(r.hourlyProfile)
        }
    }

    private fun displaySlots(r: StatsSummaryCalculator.Result) {
        val container = binding.statssummarySlotsContainer
        container.removeAllViews()
        val inflater = LayoutInflater.from(container.context)

        r.slots.forEach { slot ->
            val row = StatssummarySlotRowBinding.inflate(inflater, container, false)
            row.statssummarySlotLabel.text =
                rh.gs(R.string.statssummary_slot_label, slot.startHour, slot.endHour)
            if (slot.hasData) {
                row.statssummarySlotBar.setValues(slot.percentLow, slot.percentInRange, slot.percentHigh)
                row.statssummarySlotValue.text = rh.gs(R.string.statssummary_percent, slot.percentInRange)
            } else {
                row.statssummarySlotBar.setValues(0, 0, 0)
                row.statssummarySlotValue.text = PLACEHOLDER
            }
            container.addView(row.root)
        }
    }

    private fun formatGlucose(value: Double): String =
        if (profileUtil.units == app.aaps.core.data.model.GlucoseUnit.MMOL)
            String.format(Locale.getDefault(), "%.1f", value)
        else
            String.format(Locale.getDefault(), "%.0f", value)

    private fun periodLabel(r: StatsSummaryCalculator.Result): String =
        when (r.period) {
            StatsSummaryCalculator.Period.DAY -> dayFormat.format(Date(r.startTime))
            else                              -> {
                // Dernier jour inclus = veille de la borne de fin exclusive
                val cal = Calendar.getInstance()
                cal.timeInMillis = r.startTime
                cal.add(Calendar.DAY_OF_YEAR, r.period.days - 1)
                rh.gs(
                    R.string.statssummary_range,
                    shortFormat.format(Date(r.startTime)),
                    shortFormat.format(cal.time)
                )
            }
        }

    private fun periodSublabel(r: StatsSummaryCalculator.Result): String {
        if (r.offset == 0) {
            val nowLabel = when (r.period) {
                StatsSummaryCalculator.Period.DAY -> rh.gs(R.string.statssummary_today)
                else                              -> rh.gs(R.string.statssummary_current_period)
            }
            return rh.gs(
                R.string.statssummary_until,
                nowLabel,
                timeFormat.format(Date(r.endTime))
            )
        }
        return ""
    }

    companion object {

        private const val PLACEHOLDER = "--"
        private val dayFormat = SimpleDateFormat("EEEE d MMMM", Locale.getDefault())
        private val shortFormat = SimpleDateFormat("d MMM", Locale.getDefault())
        private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    }
}
