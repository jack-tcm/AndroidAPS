package app.aaps.plugins.main.general.statsSummary

import android.content.Context
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.keys.IntKey
import app.aaps.core.validators.preferences.AdaptiveIntPreference
import app.aaps.plugins.main.R
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Community patch — onglet Stats.
 *
 * S'active depuis Configuration → Général, comme tous les autres plugins :
 * la case de gauche active le plugin, celle de droite l'affiche en onglet
 * en haut de l'écran principal.
 *
 * Non activé par défaut : il faut le cocher volontairement.
 */
@Singleton
class StatsSummaryPlugin @Inject constructor(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.GENERAL)
        .fragmentClass(StatsSummaryFragment::class.java.name)
        .pluginIcon(app.aaps.core.objects.R.drawable.ic_cp_stats)
        .pluginName(R.string.statssummary)
        .shortName(R.string.statssummary_short)
        .preferencesId(PluginDescription.PREFERENCE_SCREEN)
        .description(R.string.description_statssummary),
    aapsLogger, rh
) {

    override fun addPreferenceScreen(preferenceManager: PreferenceManager, parent: PreferenceScreen, context: Context, requiredKey: String?) {
        if (requiredKey != null) return
        val category = PreferenceCategory(context)
        parent.addPreference(category)
        category.apply {
            key = "statssummary_settings"
            title = rh.gs(R.string.statssummary)
            initialExpandedChildrenCount = 0
            addPreference(
                AdaptiveIntPreference(
                    ctx = context, intKey = IntKey.StatsSummarySlot1Start,
                    title = R.string.statssummary_slot1_start,
                    summary = R.string.statssummary_slot_start_summary
                )
            )
            addPreference(
                AdaptiveIntPreference(
                    ctx = context, intKey = IntKey.StatsSummarySlot2Start,
                    title = R.string.statssummary_slot2_start,
                    summary = R.string.statssummary_slot_start_summary
                )
            )
            addPreference(
                AdaptiveIntPreference(
                    ctx = context, intKey = IntKey.StatsSummarySlot3Start,
                    title = R.string.statssummary_slot3_start,
                    summary = R.string.statssummary_slot_start_summary
                )
            )
            addPreference(
                AdaptiveIntPreference(
                    ctx = context, intKey = IntKey.StatsSummarySlot4Start,
                    title = R.string.statssummary_slot4_start,
                    summary = R.string.statssummary_slot_start_summary
                )
            )
            addPreference(
                AdaptiveIntPreference(
                    ctx = context, intKey = IntKey.StatsSummarySmoothingMinutes,
                    title = R.string.statssummary_smoothing,
                    summary = R.string.statssummary_smoothing_summary
                )
            )
        }
    }
}
