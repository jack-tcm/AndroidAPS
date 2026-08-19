package app.aaps.plugins.main.general.statsSummary

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.ResourceHelper
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
        .description(R.string.description_statssummary),
    aapsLogger, rh
)
