# Contexte technique — fork AAPS de Jak

> Document destiné à être collé en début de conversation avec Claude pour
> reprendre la maintenance sans tout réexpliquer.

## Base

- Upstream : `nightscout/AndroidAPS`
- Version de référence : **3.4.2.6**, commit `598e2eb`, 2 août 2026
- Branche locale : `notification-graph`
- Build : JDK 21, `compileSdk 36`, Gradle 9.0.0, variante `fullRelease`
- Environnement : Android Studio sous Windows
- Keystore : celui de l'utilisateur, **le même que l'APK installé**
  (sinon désinstallation obligatoire, perte de la progression des objectifs)

## Configuration matérielle de l'utilisateur

Elle conditionne plusieurs choix d'implémentation :

- Pompe **Omnipod** → mettre à jour l'APK au changement de pod
- Deux sources CGM en alternance : **Dexcom G6 via xDrip+** et
  **FreeStyle Libre via Juggluco**, jamais les deux actives simultanément
- Juggluco émet **une mesure par minute** (contre 5 min pour un Dexcom) —
  c'est la cause de deux bugs corrigés (couverture, densité du graphique)
- Source vue par AAPS : toujours `xDrip+` (Juggluco relaie en broadcast),
  donc AAPS ne sait pas quel capteur physique est derrière
- Unités **mg/dL**, repères d'affichage 72 / 180
- Alimentation cétogène, pas de petit-déjeuner → pas de COB le matin

## Les quatre patchs

### A — Graphique dans la notification AAPS
`NotificationGraphDrawer.kt` (nouveau) + `PersistentNotificationPlugin.kt`

`BigPictureStyle` : la vue repliée est **inchangée**, le graphique 3 h
n'apparaît qu'à l'expansion. Deux interrupteurs en tête du plugin :
`SHOW_GRAPH_IN_NOTIFICATION` et `LOCKSCREEN_VISIBLE` (ce dernier met la
notification en `VISIBILITY_PUBLIC`).

### B — Onglet Stats
18 fichiers. Plugin `GENERAL` non activé par défaut, activable depuis
Config Builder → Général.

Périodes 24 h / 7 j / 30 j en **jours calendaires**, navigation par flèches.
Indicateurs : TIR, couverture, moyenne, HbA1c estimée (ADAG), insuline
(cumul sur 24 h, moyenne journalière au-delà), répartition basal/bolus.
Graphique : courbe brute en 24 h, profil médian horaire + zone
interquartile en 7 j / 30 j. Tranches horaires à bornes configurables.

### C — Mini-graphique Juggluco
`RemoteGlucose.java`, dépôt `j-kaltes/Juggluco` (GPL-3.0, JDK 17, NDK requis).
Sparkline des ~36 dernières valeurs sous la valeur/flèche. **Indépendant des
patchs AAPS**, à re-appliquer séparément.

### D — Action Automation « Changer SMB » + indicateur
13 fichiers. Choix de la cible (`use_smb` maître ou `enableSMB_always`),
durée avec retour automatique, indicateur dans la barre de statuts.

## Décisions de conception à ne pas défaire

- **Tout est en lecture seule côté données.** Aucun patch n'écrit en base,
  n'appelle le système de contraintes, ni n'agit sur la pompe. Le patch D
  modifie des préférences qui existent déjà et sont éditables à la main.
- **Le calcul du cache inclut les réglages d'affichage dans sa clé** (lissage,
  bornes de tranches, repères 72/180) — sinon résultat périmé après réglage.
- **Le retour automatique SMB est persisté en préférence**, pas en minuteur
  mémoire, pour survivre à un redémarrage de l'app.
- **L'échéance SMB n'est armée que si aucune n'est en cours** — les règles
  Automation se redéclenchent à chaque cycle tant que leurs conditions sont
  vraies, ce qui repousserait l'échéance indéfiniment.
- **Le choix de cible SMB est persisté par nom d'enum**, pas par libellé
  traduit.
- Le sous-échantillonnage du profil horaire (1 valeur / 5 min) ne s'applique
  **qu'au profil moyen**, jamais aux indicateurs chiffrés.

## Bugs déjà rencontrés — ne pas les réintroduire

1. **Cache dupliqué** — j'ai ajouté un second cache dans
   `StatsSummaryCalculator.kt` sans voir celui qui existait déjà : 31 erreurs
   de compilation (`Conflicting declarations`). **Toujours relire le fichier
   entier avant d'ajouter une structure transverse.**
2. **Dépassement d'entier** — `var lastKept = Long.MIN_VALUE` puis
   `gv.timestamp - lastKept` déborde et rend la condition toujours vraie :
   le profil 7 j / 30 j sortait vide (bloc noir). Utiliser un `Long?`.
3. **Couverture faussée** — calculée en supposant une mesure toutes les
   5 min, elle plafonnait à 100 % avec Juggluco. Recalculée sur les
   intervalles réels, plafonnés à 10 min.
4. **Repères de couleur** — j'avais utilisé `getTargetLowMgdl/HighMgdl` (la
   cible de l'algorithme, ~115) au lieu de `UnitDoubleKey.OverviewLowMark` /
   `OverviewHighMark` (72/180). Toujours ces dernières pour l'affichage.
5. **Action SMB inopérante** — basculer `use_smb` seul ne délivre aucun SMB
   sans COB : c'est `enableSMB_always` qui compte à jeun.
6. **Test JSON strict** — `ActionSMBChangeTest` compare avec
   `JSONAssert(..., true)` : tout nouveau champ dans `toJSON()` casse le test.

## Contrôles avant de livrer un patch

```bash
# accolades équilibrées
for f in <fichiers.kt>; do o=$(grep -o "{" $f|wc -l); c=$(grep -o "}" $f|wc -l); [ $o -eq $c ] || echo "DESEQ $f"; done
# XML valide
python3 -c "import xml.dom.minidom; xml.dom.minidom.parse('<fichier.xml>')"
# chaînes référencées vs définies
grep -oh "R\.string\.[a-z_]*" *.kt | sed 's/R\.string\.//' | sort -u > /tmp/u
grep -oh 'name="[a-z_]*"' strings.xml | sed 's/name="//;s/"//' | sort -u > /tmp/d
comm -23 /tmp/u /tmp/d   # doit être vide
# membres de classe en double (indentation 4)
grep -oE "^    (private )?(fun|val|class) [A-Za-z]+" <fichier.kt> | sort | uniq -d
```

**Je n'ai jamais pu compiler** : pas d'Android SDK dans l'environnement.
Tout patch livré est vérifié à la lecture uniquement, et doit être annoncé
comme tel.

## Format de livraison attendu

Zip contenant **uniquement les fichiers touchés**, avec l'arborescence
complète depuis la racine du dépôt, plus un `LISEZ-MOI.md`. L'utilisateur
décompresse à la racine, vérifie les diffs, commit, build.

Signaler systématiquement les fichiers **partagés et volumineux**
(`plugins/main/res/values/strings.xml`, `OverviewFragment.kt`,
`core/keys/*`) : ce sont eux qui produisent des conflits au rebase.

## Limites que je maintiens

L'utilisateur a demandé à plusieurs reprises de retirer ou contourner les
objectifs AAPS (verrous de sécurité sur boucle fermée, SMB, automation).
J'ai refusé et je maintiens : ce sont des garde-fous sur un logiciel qui
délivre de l'insuline. Même chose pour une action Automation qui injecterait
un bolus sans validation.

En revanche : affichage, statistiques, confort d'usage, et tout ce qui
**borne** un effet existant (comme la durée SMB) sont sans problème.
