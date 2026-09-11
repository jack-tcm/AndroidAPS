package app.aaps.plugins.automation.actions

import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.LongNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.utils.JsonHelper
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.InputDropdownMenu
import app.aaps.plugins.automation.elements.InputDropdownOnOffMenu
import app.aaps.plugins.automation.elements.InputDuration
import app.aaps.plugins.automation.elements.LabelWithElement
import app.aaps.plugins.automation.elements.LayoutBuilder
import dagger.android.HasAndroidInjector
import org.json.JSONObject
import javax.inject.Inject

class ActionSMBChange(injector: HasAndroidInjector) : Action(injector) {

    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var preferences: Preferences

    /**
     * Community patch — quelle option SMB est modifiée.
     *
     * MASTER (use_smb) est l'interrupteur maître : il autorise les SMB, mais
     * l'algorithme décide ensuite quand les délivrer selon les sous-options
     * (avec COB, après glucides, avec cible temp basse...). L'activer seul ne
     * produit donc aucun SMB en l'absence de glucides.
     *
     * ALWAYS (enableSMB_always) est la sous-option qui autorise les SMB en
     * permanence, sans glucides ni cible temp. C'est celle qu'il faut pour
     * agir à jeun, par exemple sur un effet de l'aube.
     */
    enum class SmbTarget(
        val key: BooleanKey,
        /** Échéance du retour automatique, propre à cette cible. */
        val revertAtKey: LongNonKey,
        /** État à restaurer, propre à cette cible. */
        val revertValueKey: BooleanNonKey,
        @StringRes val label: Int
    ) {
        MASTER(
            BooleanKey.ApsUseSmb,
            LongNonKey.AutomationSmbRevertAtMaster,
            BooleanNonKey.AutomationSmbRevertValueMaster,
            R.string.smbTargetMaster
        ),
        ALWAYS(
            BooleanKey.ApsUseSmbAlways,
            LongNonKey.AutomationSmbRevertAtAlways,
            BooleanNonKey.AutomationSmbRevertValueAlways,
            R.string.smbTargetAlways
        )
    }

    var smbState: InputDropdownOnOffMenu = InputDropdownOnOffMenu(rh, true)
    var smbTarget: InputDropdownMenu = InputDropdownMenu(rh)

    /**
     * Source de vérité de la cible choisie.
     *
     * Le menu déroulant stocke le **libellé traduit**, ce qui est fragile :
     * si le mapping libellé → enum échoue (liste pas encore peuplée, ordre
     * de rappel du Spinner, libellé changé par une traduction), on perdait
     * silencieusement le choix et l'enregistrement suivant figeait MASTER.
     *
     * Ce champ conserve la dernière valeur connue et n'est mis à jour que
     * lorsqu'un libellé correspond réellement à une entrée de l'enum.
     */
    private var target: SmbTarget = SmbTarget.MASTER

    /**
     * Durée en minutes. 0 = changement permanent (comportement d'origine).
     * > 0 = l'état précédent est restauré automatiquement à l'expiration.
     */
    var duration: InputDuration = InputDuration(0, InputDuration.TimeUnit.MINUTES)

    init {
        smbTarget.setValue(rh.gs(target.label))
    }

    /**
     * Remappe le libellé du menu vers l'enum. En cas de correspondance, la
     * source de vérité est mise à jour ; sinon la dernière valeur connue est
     * conservée — jamais de retour silencieux sur MASTER.
     */
    private fun selectedTarget(): SmbTarget {
        SmbTarget.entries.firstOrNull { rh.gs(it.label) == smbTarget.value }?.let { target = it }
        return target
    }

    override fun friendlyName(): Int = R.string.changeSmbState
    override fun shortDescription(): String {
        val what = rh.gs(selectedTarget().label)
        return if (duration.value > 0)
            rh.gs(R.string.changeSmbToForDuration, what, smbState.toTextValue(), duration.value)
        else
            rh.gs(R.string.changeSmbTo2, what, smbState.toTextValue())
    }

    @DrawableRes override fun icon(): Int = app.aaps.core.ui.R.drawable.ic_running_mode

    override fun doAction(callback: Callback) {
        val applied = selectedTarget()
        if (duration.value > 0) {
            // La règle peut se redéclencher à chaque cycle tant que ses
            // conditions restent vraies. On n'arme l'échéance que si aucune
            // n'est en cours POUR CETTE CIBLE, sinon elle serait repoussée
            // indéfiniment et le retour n'aurait jamais lieu.
            //
            // Le suivi étant par cible, deux règles visant des options
            // différentes peuvent avoir un retour en attente simultanément
            // sans s'écraser.
            if (preferences.get(applied.revertAtKey) == 0L) {
                preferences.put(applied.revertValueKey, preferences.get(applied.key))
                preferences.put(applied.revertAtKey, dateUtil.now() + duration.value * 60_000L)
            }
        } else {
            // Changement permanent : on annule un retour en attente sur cette
            // cible uniquement, sinon il viendrait l'écraser.
            preferences.put(applied.revertAtKey, 0L)
        }
        preferences.put(applied.key, smbState.value)
        callback.result(pumpEnactResultProvider.get().success(true).comment(app.aaps.core.ui.R.string.ok)).run()
    }

    override fun generateDialog(root: LinearLayout) {
        smbTarget.setList(ArrayList(SmbTarget.entries.map { rh.gs(it.label) as CharSequence }))
        // Resynchronise l'affichage sur la source de vérité avant ouverture :
        // la liste vient seulement d'être peuplée.
        smbTarget.setValue(rh.gs(target.label))
        LayoutBuilder()
            .add(LabelWithElement(rh, rh.gs(R.string.smbTargetLabel), "", smbTarget))
            .add(LabelWithElement(rh, rh.gs(R.string.newSmbMode), "", smbState))
            .add(LabelWithElement(rh, rh.gs(R.string.smbDuration), "", duration))
            .build(root)
    }

    override fun hasDialog(): Boolean = true

    override fun toJSON(): String {
        val data = JSONObject()
            .put("smbState", smbState.value)
            // Nom de l'enum, pas le libellé : indépendant de la langue
            .put("smbTarget", selectedTarget().name)
            .put("durationInMinutes", duration.value)
        return JSONObject()
            .put("type", this.javaClass.simpleName)
            .put("data", data)
            .toString()
    }

    override fun fromJSON(data: String): Action {
        val o = JSONObject(data)
        smbState.value = JsonHelper.safeGetBoolean(o, "smbState", true)
        // Champs absents des règles créées avant ce patch : on retombe sur le
        // comportement d'origine (interrupteur maître, permanent).
        target = SmbTarget.entries.firstOrNull {
            it.name == JsonHelper.safeGetString(o, "smbTarget", SmbTarget.MASTER.name)
        } ?: SmbTarget.MASTER
        smbTarget.setValue(rh.gs(target.label))
        duration.value = JsonHelper.safeGetInt(o, "durationInMinutes", 0)
        return this
    }

    override fun isValid(): Boolean = true
}
