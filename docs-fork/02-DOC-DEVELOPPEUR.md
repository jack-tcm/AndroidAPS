# Fork AAPS — documentation de maintenance

Base : **AAPS 3.4.2.6** (`598e2eb`, 02/08/2026) · branche `notification-graph`

## 1. Vue d'ensemble

Quatre patchs, dont trois sur AAPS et un sur Juggluco.

| # | Patch | Fichiers | Zone |
|---|---|---|---|
| A | Graphique dans la notification | 2 | `plugins/main` |
| B | Onglet Stats | 18 | `plugins/main`, `core/keys`, `app` |
| C | Mini-graphique Juggluco | 1 | dépôt Juggluco |
| D | Action SMB + indicateur | 13 | `plugins/automation`, `plugins/main`, `core/keys` |

Les patchs A et B partagent des fichiers (`PersistentNotificationPlugin.kt`
est dans les deux lots). Le zip B les contient tous les deux : c'est lui la
référence pour A.

## 2. Inventaire par fichier

### Fichiers créés (aucun conflit possible au rebase)

```
plugins/main/.../general/persistentNotification/NotificationGraphDrawer.kt
plugins/main/.../general/statsSummary/StatsSummaryPlugin.kt
plugins/main/.../general/statsSummary/StatsSummaryFragment.kt
plugins/main/.../general/statsSummary/StatsSummaryCalculator.kt
plugins/main/.../general/statsSummary/GlucoseChartView.kt
plugins/main/.../general/statsSummary/TirBarView.kt
plugins/main/.../general/statsSummary/SplitBarView.kt
plugins/main/.../di/StatsSummaryModule.kt
plugins/main/res/layout/statssummary_fragment.xml
plugins/main/res/layout/statssummary_slot_row.xml
plugins/main/res/drawable/ic_statssummary_prev.xml
plugins/main/res/drawable/ic_statssummary_next.xml
plugins/main/res/values/statssummary_strings.xml
plugins/main/res/values-fr/statssummary_strings.xml
```

### Fichiers modifiés — par risque de conflit décroissant

| Fichier | Ampleur | Risque |
|---|---|---|
| `plugins/main/res/values/strings.xml` | +3 lignes | **élevé** — fichier partagé, très actif en amont |
| `plugins/main/res/values-fr-rFR/strings.xml` | +3 lignes | **élevé** — régénéré par Crowdin en amont |
| `core/keys/.../IntKey.kt` | +6 clés | moyen |
| `core/keys/.../BooleanNonKey.kt` | +2 clés | moyen |
| `core/keys/.../LongNonKey.kt` | +1 clé | moyen |
| `app/.../di/PluginsListModule.kt` | +2 lignes | moyen |
| `plugins/automation/res/values/strings.xml` | +5 lignes | moyen |
| `plugins/automation/res/values-fr-rFR/strings.xml` | +5 lignes | moyen |
| `plugins/main/.../overview/ui/StatusLightHandler.kt` | +1 param, +4 fonctions, +1 dépendance | moyen |
| `plugins/main/.../overview/OverviewFragment.kt` | +1 ligne | faible |
| `plugins/main/res/layout/overview_statuslights_layout.xml` | +1 bloc | faible |
| `plugins/main/.../di/PluginsModule.kt` | +1 ligne | faible |
| `plugins/main/.../PersistentNotificationPlugin.kt` | ~35 lignes | faible |
| `plugins/automation/.../AutomationPlugin.kt` | +13 lignes | faible |
| `plugins/automation/.../actions/ActionSMBChange.kt` | réécrit | faible |
| `plugins/automation/src/test/.../ActionSMBChangeTest.kt` | adapté | faible |

Tous les ajouts sont encadrés par un commentaire `// Community patch` ou
`<!-- Community patch -->` — c'est le repère à chercher lors d'un rebase.

## 3. Architecture des patchs

### A — Notification

`BigPictureStyle` est ajouté au `NotificationCompat.Builder` existant : la
vue repliée n'est pas touchée, seule l'expansion affiche le bitmap. Le
dessin se fait sur un `Canvas` 1024×384 dans `NotificationGraphDrawer`, qui
lit `PersistenceLayer.getBgReadingsDataFromTimeToTime` et les repères
`UnitDoubleKey.OverviewLowMark` / `OverviewHighMark`.

Deux interrupteurs en tête de `PersistentNotificationPlugin` :
`SHOW_GRAPH_IN_NOTIFICATION` (false = comportement d'origine) et
`LOCKSCREEN_VISIBLE` (`VISIBILITY_PUBLIC` — expose la glycémie sur l'écran
verrouillé).

### B — Onglet Stats

Découpage classique plugin / fragment / calculateur :

- `StatsSummaryPlugin` — `PluginType.GENERAL`, `PREFERENCE_SCREEN` pour ses
  5 préférences (4 bornes de tranches + lissage), enregistré via
  `@IntKey(620)` dans `PluginsListModule`
- `StatsSummaryCalculator` — tout le calcul, thread `io`, avec cache
- `StatsSummaryFragment` — affichage, thread `main`, `ViewBinding`
- trois vues personnalisées : `GlucoseChartView` (deux modes),
  `TirBarView`, `SplitBarView`

Points d'implémentation notables :

**Cache** — `LinkedHashMap` de 12 entrées. La clé contient période, décalage,
horodatage de début, repères 72/180, lissage et bornes de tranches : changer
un réglage produit naturellement un défaut de cache. Une période passée est
figée pour la session, la période en cours est revalidée périodiquement.

**Sous-échantillonnage** — le profil horaire ne retient qu'une valeur toutes
les 5 min (~43 000 → ~8 600 points sur 30 j). Les indicateurs chiffrés
restent calculés sur la totalité.

**Lissage du graphique 24 h** — regroupement par tranches de N minutes puis
**médiane** de chaque tranche (pas moyenne : préserve les vrais pics).
Réglable, 5 min par défaut, 0 = désactivé. N'affecte que le tracé.

**Couverture** — somme des intervalles entre mesures consécutives, chacun
plafonné à 10 min, rapportée à la durée de la période. Indépendant de la
cadence de la source.

**Concurrence** — le fragment vérifie après calcul que la vue existe encore
et que l'onglet n'a pas changé, sinon il ignore le résultat. Barre de
progression affichée seulement après 250 ms.

### D — Action SMB

Le retour automatique est suivi **par cible**, chacune portant ses propres
clés — c'est l'enum `SmbTarget` qui les associe :

| Cible | Préférence agie | Échéance | Valeur à restaurer |
|---|---|---|---|
| MASTER | `ApsUseSmb` | `AutomationSmbRevertAtMaster` | `AutomationSmbRevertValueMaster` |
| ALWAYS | `ApsUseSmbAlways` | `AutomationSmbRevertAtAlways` | `AutomationSmbRevertValueAlways` |

Ce découplage est nécessaire : deux règles visant des options différentes
peuvent avoir un retour en attente simultanément. Avec un jeu de clés unique,
la seconde règle n'armait rien et sa modification n'était jamais annulée.

L'expiration parcourt `SmbTarget.entries` ; l'indicateur affiche l'échéance
la plus proche des deux.

`AutomationPlugin.processActions()` vérifie l'échéance à chaque cycle, **avant**
les contrôles de boucle suspendue. Persistance en préférence plutôt qu'en
minuteur mémoire : le retour survit à un redémarrage de l'app.

Le paramètre ajouté à `updateStatusLights()` a une valeur par défaut `null`,
pour que `ActionsFragment` — autre appelant — compile sans modification.

**Indicateur SMB.** `StatusLightHandler` reproduit les conditions de
`enable_smb` plutôt que de lire une préférence isolée : maître actif **et**
au moins une sous-option satisfaite dans le contexte du moment.

| Sous-option | Source de la condition |
|---|---|
| `ApsUseSmbAlways` | toujours vraie |
| `ApsUseSmbWithCob` | `IobCobCalculator.getCobInfo().displayCob > 0` |
| `ApsUseSmbAfterCarbs` | `persistenceLayer.getCarbsFromTimeExpanded(now - 6 h)` |
| `ApsUseSmbWithLowTt` / `WithHighTt` | `persistenceLayer.getTemporaryTargetActiveAt(now)` |

Cela ajoute **`IobCobCalculator`** aux dépendances injectées de la classe.
Chaque lecture est protégée par un `try/catch` renvoyant `false` : un
indicateur ne doit jamais faire tomber l'écran d'accueil.

## 4. Procédure de mise à jour vers une nouvelle version d'AAPS

```bash
git fetch upstream
git checkout notification-graph
git rebase upstream/master        # ou le tag visé
```

Ordre de résolution conseillé, du plus probable au moins :

1. **`plugins/main/res/values/strings.xml`** et les `values-*-r*` — presque
   toujours en conflit. Garder les deux côtés : les chaînes amont **et** les
   `statssummary_*` / `smb_status*`.
2. **`core/keys/*.kt`** — les clés sont ajoutées en fin d'enum. Conflit si
   l'amont en ajoute aussi. Garder les deux, vérifier qu'aucune clé de
   préférence (la chaîne entre guillemets) n'est en double.
3. **`PluginsListModule.kt`** — vérifier que `@IntKey(620)` reste unique :
   `grep -o "IntKey([0-9]*)" | sort -n | uniq -d`
4. Le reste se rebase généralement sans intervention.

Puis, avant de builder :

```bash
git diff upstream/master --stat        # doit lister ~30 fichiers, pas plus
./gradlew :plugins:main:compileFullReleaseKotlin
./gradlew :plugins:automation:testFullReleaseUnitTest
```

## 5. Points de fragilité connus

**`AdaptiveIntPreference`** — construit par arguments nommés (`ctx`,
`intKey`, `title`, `summary`). Signature déjà modifiée en amont par le passé.

**ViewBinding** — les noms générés (`binding.statusLightsLayout.smbStatus`,
`StatssummaryFragmentBinding`) dépendent des `android:id`. Renommer un id
casse silencieusement à la compilation.

**`values-fr` vs `values-fr-rFR`** — les chaînes de l'onglet Stats sont dans
`values-fr`, celles des autres patchs dans `values-fr-rFR`. Android résout
par repli, donc ça fonctionne, mais c'est incohérent. À uniformiser vers
`values-fr-rFR` à la prochaine occasion.

**Tests** — `ActionSMBChangeTest` utilise `JSONAssert(..., strict=true)` :
tout champ ajouté à `toJSON()` casse le test.

## 6. Ce qui n'a jamais été fait

- **Aucun patch n'a été compilé** par son auteur (Claude) : pas d'Android SDK
  dans son environnement. Toute livraison est vérifiée à la lecture.
- **Aucune contribution en amont.** Ces patchs ne sont pas soumis à AAPS et
  n'ont pas vocation à l'être en l'état.
- **Pas de tests automatisés** sur les patchs A, B et l'indicateur.
- Requête base non optimisée : `getBgReadingsDataFromTimeToTime` charge des
  objets `GV` complets là où deux champs suffiraient. C'est le plancher de
  performance restant sur 30 j. L'optimiser demanderait de toucher à la
  couche DAO, donc plus de surface de conflit.
