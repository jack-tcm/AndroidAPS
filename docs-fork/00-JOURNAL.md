# Journal des évolutions du fork

Ordre antichronologique. Chaque entrée indique les fichiers touchés, pour
retrouver rapidement l'origine d'un comportement.

---

## 2026-09-10 — Cible SMB perdue à la réinstallation + message pod

**Correctif.** La cible choisie (« SMB toujours ») repassait sur
l'interrupteur maître après installation d'un APK.

L'état était dérivé du **libellé traduit** affiché par le menu déroulant :
`selectedTarget()` remappait ce libellé vers l'enum et retombait sur MASTER
en cas d'échec. Le premier enregistrement figeait alors cette perte.

Un champ `target` devient la source de vérité. Il n'est mis à jour que
lorsqu'un libellé correspond réellement à une entrée de l'enum — jamais de
retour silencieux sur MASTER. Le menu est resynchronisé à l'ouverture du
dialogue, une fois sa liste peuplée.

`ActionSMBChange.kt`

**Message d'activation du pod.** `omnipod_dash_scan_failed` complété : si le
pod a émis ses deux bips, il fonctionne, et c'est le scan Bluetooth du
téléphone qui est bloqué — redémarrer.

Troisième occurrence du même problème (voir doc support).

`pump/omnipod/dash/res/values/strings.xml` et `values-fr-rFR/`

---

## 2026-09-07 — Retour SMB : suivi par cible

**Correctif.** Le retour automatique ne gérait qu'une seule échéance. Deux
règles visant des options différentes — l'aube sur « SMB toujours », une
règle hypo sur le maître — entraient en collision :

1. l'aube arme un retour jusqu'à 09:30
2. la règle hypo se déclenche à 07:00, coupe le maître, mais n'arme rien
   (une échéance existe déjà)
3. à 09:30 le retour rend son état à « SMB toujours » — **le maître reste
   coupé indéfiniment**

Défaillance silencieuse, dans la direction sûre (moins d'insuline) mais
pouvant durer des jours.

Chaque cible porte désormais ses propres clés d'échéance et de valeur, via
l'enum `SmbTarget`. L'expiration parcourt les deux. L'indicateur affiche
l'échéance la plus proche.

`LongNonKey.kt`, `BooleanNonKey.kt`, `ActionSMBChange.kt`,
`AutomationPlugin.kt`, `StatusLightHandler.kt`

---

## 2026-09-05 — Indicateur SMB : texte seul

L'icône de seringue est retirée : l'indicateur n'est plus qu'un libellé
« SMB » en gras, coloré selon l'état. Plus compact et plus lisible dans une
barre déjà chargée.

Le `TextView` devient enfant direct du flexbox ; l'identifiant `smb_status`
est inchangé, donc le nom ViewBinding aussi.

`overview_statuslights_layout.xml`

---

## 2026-09-05 — Indicateur SMB : conditions réelles

**Correctif.** L'indicateur testait `use_smb ET enableSMB_always`. Avec
« SMB en permanence » sur OFF, il restait gris en permanence — y compris
quand des SMB partaient réellement grâce aux glucides actifs.

Il reproduit désormais les conditions de `enable_smb` de l'algorithme :
maître actif **et** au moins une sous-option satisfaite dans le contexte du
moment (COB > 0, glucides dans les 6 h, cible temporaire active).

Distinction clé : « les SMB sont autorisés » ≠ « un SMB peut partir
maintenant ». C'est la seconde qui est affichée.

`StatusLightHandler.kt` — dépendance ajoutée : `IobCobCalculator`

---

## 2026-09-05 — Documentation

Création de `docs-fork/` : contexte pour Claude, doc développeur,
explication non technique, guide utilisateur, maintenance, support.

Section support complétée avec l'incident Nightscout (504 sur
`bundle.app.js`, droits du cache).

---

## 2026-09-05 — Action SMB : choix de la cible

**Correctif majeur.** L'action Automation ne modifiait que `use_smb`,
l'interrupteur maître. Sans glucides déclarés, aucun SMB n'était délivré :
la règle « Effet de l'aube » se déclenchait toutes les 5 min sans effet.

Ajout d'un champ **Réglage SMB** : interrupteur maître (défaut, rétro-
compatible) ou `enableSMB_always`. Choix persisté par nom d'enum, insensible
à la langue.

Garde-fou ajouté : l'échéance de durée n'est armée que si aucune n'est en
cours, sinon les redéclenchements successifs la repousseraient indéfiniment.

`ActionSMBChange.kt`, `AutomationPlugin.kt`, `BooleanNonKey.kt`, strings,
`ActionSMBChangeTest.kt`

---

## 2026-09-05 — Indicateur SMB dans la barre de statuts

Ajout d'un indicateur à côté de l'âge du pod, du réservoir et du capteur.
Affiche le temps restant quand une activation minutée est en cours.

Placé là plutôt que dans le bandeau principal : moins de surface de conflit
au rebase.

`StatusLightHandler.kt`, `OverviewFragment.kt` (1 ligne),
`overview_statuslights_layout.xml`, strings

---

## 2026-09-03 — Durée sur l'action SMB

Champ **Durée** en minutes. 0 = permanent. Au-delà, l'état précédent est
restauré automatiquement.

Échéance persistée en préférence et relue à chaque cycle : le retour survit
à un redémarrage de l'app. Vérification placée avant les contrôles de boucle
suspendue.

`ActionSMBChange.kt`, `AutomationPlugin.kt`, `LongNonKey.kt`,
`BooleanNonKey.kt`, strings, test

---

## 2026-08-20 — Stats : correctif profil 7 j / 30 j

**Bug.** `var lastKept = Long.MIN_VALUE` puis `gv.timestamp - lastKept`
déborde : condition toujours vraie, aucune valeur retenue, profil vide,
bloc noir à l'écran.

L'optimisation de la veille ne faisait donc rien gagner non plus.

`StatsSummaryCalculator.kt`

---

## 2026-08-20 — Stats : correctif compilation

**Bug.** Un second cache ajouté alors qu'il en existait déjà un : 31 erreurs
(`Conflicting declarations`). Doublon supprimé, version d'origine conservée
(sa clé inclut `startTime`, ce qui gère le passage de minuit).

`StatsSummaryCalculator.kt`

---

## 2026-08-19 — Stats : performance

Barre de progression (après 250 ms), cache de 12 entrées avec les réglages
dans la clé, sous-échantillonnage du profil horaire (1 valeur / 5 min).

`StatsSummaryCalculator.kt`, `StatsSummaryFragment.kt`,
`statssummary_fragment.xml`

---

## 2026-08-19 — Stats : courbe colorée

Vert / jaune / rouge selon les repères 72 et 180, avec découpe exacte au
point de franchissement par interpolation.

`GlucoseChartView.kt`

---

## 2026-08-19 — Stats : lissage du graphique

Regroupement par tranches de N minutes puis **médiane** (pas moyenne :
préserve les vrais pics). Réglable, 5 min par défaut, 0 = désactivé.
N'affecte que le tracé 24 h.

Motivé par la densité de Juggluco : une mesure par minute, soit ~1400 points
sur ~1000 pixels.

`StatsSummaryCalculator.kt`, `StatsSummaryPlugin.kt`, `IntKey.kt`, strings

---

## 2026-08-19 — Stats : graphique et tranches horaires

Graphique en deux modes : courbe brute en 24 h, profil médian horaire avec
zone interquartile en 7 j / 30 j. Quatre tranches horaires à bornes
configurables.

Correctif au passage : la couverture supposait une mesure toutes les 5 min
et plafonnait à 100 % avec Juggluco. Recalculée sur les intervalles réels.

`GlucoseChartView.kt`, `StatsSummaryCalculator.kt`,
`StatsSummaryFragment.kt`, `IntKey.kt`, layouts, strings

---

## 2026-08-18 — Onglet Stats

Plugin `GENERAL` non activé par défaut. Périodes 24 h / 7 j / 30 j en jours
calendaires, navigation par flèches, TIR, couverture, moyenne, HbA1c
estimée, insuline et glucides, répartition basal/bolus.

18 fichiers, dont `PluginsListModule.kt` (`@IntKey(620)`).

---

## 2026-08-18 — Notification : repères corrigés

**Bug.** Utilisation de `getTargetLowMgdl/HighMgdl` — la cible de
l'algorithme, ~115 — au lieu des repères d'affichage.

Remplacé par `UnitDoubleKey.OverviewLowMark` / `OverviewHighMark` (72/180).
Bande de fond verte ajoutée.

`NotificationGraphDrawer.kt`

---

## 2026-08-09 — Graphique dans la notification

`BigPictureStyle` : vue repliée inchangée, graphique 3 h à l'expansion.
Visibilité sur écran verrouillé, désactivable.

`NotificationGraphDrawer.kt` (nouveau), `PersistentNotificationPlugin.kt`

---

## 2026-08 — Mini-graphique Juggluco

Sparkline des ~36 dernières valeurs dans la notification Juggluco.
Dépôt séparé, chaîne d'outils différente (JDK 17, NDK).

`RemoteGlucose.java`
