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
    enum class SmbTarget(val key: BooleanKey, @StringRes val label: Int) {
        MASTER(BooleanKey.ApsUseSmb, R.string.smbTargetMaster),
        ALWAYS(BooleanKey.ApsUseSmbAlways, R.string.smbTargetAlways)
    }

    var smbState: InputDropdownOnOffMenu = InputDropdownOnOffMenu(rh, true)
    var smbTarget: InputDropdownMenu = InputDropdownMenu(rh)

    /**
     * Durée en minutes. 0 = changement permanent (comportement d'origine).
     * > 0 = l'état précédent est restauré automatiquement à l'expiration.
     */
    var duration: InputDuration = InputDuration(0, InputDuration.TimeUnit.MINUTES)

    init {
        smbTarget.setValue(rh.gs(SmbTarget.MASTER.label))
    }

    /** Le menu stocke le libellé affiché : on le remappe vers l'enum. */
    private fun selectedTarget(): SmbTarget =
        SmbTarget.entries.firstOrNull { rh.gs(it.label) == smbTarget.value } ?: SmbTarget.MASTER

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
        val target = selectedTarget()
        if (duration.value > 0) {
            // La règle peut se redéclencher à chaque cycle tant que ses
            // conditions restent vraies. On n'arme l'échéance que si aucune
            // n'est en cours, sinon elle serait repoussée indéfiniment et le
            // retour n'aurait jamais lieu.
            if (preferences.get(LongNonKey.AutomationSmbRevertAt) == 0L) {
                preferences.put(BooleanNonKey.AutomationSmbRevertValue, preferences.get(target.key))
                preferences.put(BooleanNonKey.AutomationSmbRevertIsAlways, target == SmbTarget.ALWAYS)
                preferences.put(LongNonKey.AutomationSmbRevertAt, dateUtil.now() + duration.value * 60_000L)
            }
        } else {
            // Changement permanent : on annule un éventuel retour en attente,
            // sinon il viendrait l'écraser.
            preferences.put(LongNonKey.AutomationSmbRevertAt, 0L)
        }
        preferences.put(target.key, smbState.value)
        callback.result(pumpEnactResultProvider.get().success(true).comment(app.aaps.core.ui.R.string.ok)).run()
    }

    override fun generateDialog(root: LinearLayout) {
        smbTarget.setList(ArrayList(SmbTarget.entries.map { rh.gs(it.label) as CharSequence }))
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
        val target = SmbTarget.entries.firstOrNull {
            it.name == JsonHelper.safeGetString(o, "smbTarget", SmbTarget.MASTER.name)
        } ?: SmbTarget.MASTER
        smbTarget.setValue(rh.gs(target.label))
        duration.value = JsonHelper.safeGetInt(o, "durationInMinutes", 0)
        return this
    }

    override fun isValid(): Boolean = true
}
