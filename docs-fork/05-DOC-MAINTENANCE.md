# Maintenance du fork AAPS — installation, compilation, déploiement

Base : **AAPS 3.4.2.6** · branche `notification-graph` · Windows

---

## 1. Poste de travail

### À installer une fois

| Outil | Version | Où |
|---|---|---|
| **JDK 21** | 21 (Temurin) | https://adoptium.net/temurin/releases/?version=21 |
| **Android Studio** | dernière stable | https://developer.android.com/studio |
| **Git** | récent | https://git-scm.com |

Puis dans Android Studio → **SDK Manager** → onglet *SDK Platforms* :
- **Android SDK Platform 36**

Pas de NDK ni de CMake pour AAPS : il n'y a pas de code natif.
(Le patch Juggluco, lui, en a besoin — voir §7.)

### Configurer le JDK

**File → Settings → Build, Execution, Deployment → Build Tools → Gradle →
Gradle JDK** → sélectionner la **21**.

C'est la première chose à vérifier si le build échoue avec des erreurs
inexpliquées après une réinstallation.

### Chemin du projet

Le dépôt doit vivre dans un chemin **sans espace ni accent** :
`C:\AAPS\AndroidAPS` convient, `C:\Users\Jak\Mes Projets\` non.

---

## 2. Structure du dépôt

```
notification-graph   ← la branche de travail, celle qui contient les patchs
master               ← miroir de l'upstream, à ne pas modifier
```

Remotes attendus :
```bash
git remote -v
# origin    → ton fork GitHub
# upstream  → https://github.com/nightscout/AndroidAPS.git
```

Si `upstream` manque :
```bash
git remote add upstream https://github.com/nightscout/AndroidAPS.git
```

### Vérifier où on se trouve

```bash
git status          # doit dire "On branch notification-graph"
git describe --tags # ex. 3.4.2.6-1-g5174d31e8f
```

`HEAD detached at ...` signifie qu'on est sur un tag, pas sur la branche :
```bash
git switch notification-graph
```

Le suffixe du `git describe` (`g5174d31e8f`) correspond au numéro entre
parenthèses affiché dans les préférences AAPS (`3.4.2.6 (5174)`). C'est le
moyen le plus fiable de savoir quel commit tourne réellement sur le
téléphone.

---

## 3. Le keystore — le point critique

L'APK doit être signé avec **le même keystore que celui déjà installé**.
Sinon Android refuse l'installation par-dessus, il faut désinstaller, et la
progression des objectifs comme l'historique repartent de zéro (sauf import
des réglages).

### Sauvegarder

Le fichier `.jks` ou `.keystore`, **plus le mot de passe et l'alias**,
doivent être conservés hors du téléphone et hors du PC :
- une copie sur un support externe
- une copie dans un gestionnaire de mots de passe

**Un keystore perdu ne se régénère pas.** C'est le seul élément de toute
cette chaîne qui soit vraiment irremplaçable.

### En créer un (première fois seulement)

Build → Generate Signed App Bundle / APK → APK → **Create new…**
Choisir une validité longue (25 ans), noter alias et mots de passe.

---

## 4. Compiler

### Depuis Android Studio

**Build → Generate Signed App Bundle / APK…** → **APK** → sélectionner le
keystore → variante **fullRelease** → Finish.

APK produit dans :
```
app\build\outputs\apk\full\release\
```

### Raccourcis Android Studio

| Raccourci | Action |
|---|---|
| `Ctrl + F9` | *Make Project* — compile sans produire d'APK, retour rapide sur les erreurs |
| `Ctrl + Ctrl` | *Run Anything* — taper ensuite `gradlew assembleFullRelease` |
| `Ctrl + Shift + A` | *Find Action* — taper « Generate Signed » |

*Generate Signed App Bundle / APK* n'a pas de raccourci par défaut ; on peut
lui en assigner un via **Settings → Keymap**.

Workflow conseillé : `Ctrl + F9` après chaque modification pour valider la
compilation en quelques secondes, puis le build signé seulement quand tout
compile.

### En ligne de commande

```bash
gradlew.bat assembleFullRelease
```

Utile aussi pour isoler une erreur avant un build complet :
```bash
gradlew.bat :plugins:main:compileFullReleaseKotlin
gradlew.bat :plugins:automation:testFullReleaseUnitTest
```

Le premier build après un changement de version télécharge Gradle et les
dépendances : compter plusieurs minutes.

### Alternative sans PC : GitHub Actions

Sur ton fork GitHub → onglet **Actions** → dérouler les workflows →
**Run workflow** → branche `notification-graph`, variante `fullRelease`.
Le build terminé est déposé sur Google Drive.

Pratique en déplacement, mais nécessite d'avoir configuré les secrets de
signature dans le dépôt.

---

## 5. Déployer sur le téléphone

**Toujours dans cet ordre :**

1. **Exporter les réglages** depuis AAPS : ⋮ → Maintenance → Export settings.
   Vérifier que le fichier existe et est récupérable.
2. **Conserver l'APK actuel** quelque part d'accessible depuis le téléphone.
3. **Choisir le moment** — voir §6.
4. Transférer le nouvel APK et l'installer **par-dessus**, sans désinstaller.
5. Vérifier : ⋮ → About (version + numéro de commit), progression des
   objectifs, arrivée des glycémies, réglages OpenAPS SMB.

### Revenir en arrière

Réinstaller l'ancien APK par-dessus. Si la signature diffère : désinstaller,
réinstaller, puis **Import settings** avec le fichier de l'étape 1.

---

## 6. Choisir le moment (Omnipod)

La recommandation constante de la doc AAPS pour Omnipod est de **faire
coïncider la mise à jour avec un changement de pod**. Réinstaller l'APK tue
le processus, ce qui peut désynchroniser l'état de communication avec le pod.

Séquence recommandée :

1. Attendre la fin de vie normale du pod
2. Désactiver l'ancien pod dans AAPS
3. **Installer le nouvel APK** (pas de pod actif à ce moment)
4. Vérifier qu'AAPS démarre correctement
5. Activer le nouveau pod

Entre les étapes 2 et 5, il n'y a pas d'insuline basale : préparer tout à
l'avance (APK déjà sur le téléphone, pod et insuline prêts).

**Pour un patch purement d'affichage sur la même version d'AAPS**, le risque
est nettement plus faible — un pod actif est acceptable, en choisissant un
moment calme (IOB bas, pas en pleine correction, pas juste avant de dormir).

---

## 7. Le patch Juggluco (séparé)

Dépôt : `j-kaltes/Juggluco` (GPL-3.0). **Chaîne d'outils différente** :

- **JDK 17** (et non 21)
- NDK **30.0.14904198** et CMake **4.1.2** — il y a du code C++
- Les bibliothèques natives propriétaires ne sont pas dans le dépôt : il
  faut les extraire d'un APK officiel (renommer en `.zip`, décompresser,
  copier les `.so` du dossier `lib/` vers les `jniLibs/<abi>/` du projet)
- Keystore partagé inclus (`Common/everyone.keystore`) — l'APK produit est
  directement signé

```bash
gradlew.bat assembleMobileLibre3SiDexNogoogleReleaseLog
```

Ce patch est indépendant d'AAPS et se re-applique séparément.

---

## 8. Mettre à jour vers une nouvelle version d'AAPS

### Préparer

```bash
git status                    # arbre propre obligatoire
git branch backup-avant-rebase   # filet de sécurité
git fetch upstream --tags
```

### Rebaser

```bash
git switch notification-graph
git rebase <tag-cible>        # ex. 3.5.0.0
```

Ordre de résolution des conflits, du plus probable au moins :

1. **`plugins/main/res/values/strings.xml`** et les `values-*-r*` — presque
   toujours en conflit (Crowdin les régénère en amont). Garder les deux
   côtés : les chaînes amont **et** les `statssummary_*` / `smb_status*`.
2. **`core/keys/*.kt`** — clés ajoutées en fin d'enum. Garder les deux
   côtés, puis vérifier qu'aucune clé de préférence n'est en double.
3. **`PluginsListModule.kt`** — vérifier l'unicité :
   ```bash
   grep -o "IntKey([0-9]*)" app/src/main/kotlin/app/aaps/di/PluginsListModule.kt | sort -n | uniq -d
   ```
   Doit être vide. Sinon, changer le `620` du plugin Stats.
4. Le reste passe généralement sans intervention.

Tous les ajouts sont marqués `// Community patch` ou
`<!-- Community patch -->` : c'est le repère à chercher.

### Contrôler avant de builder

```bash
git diff <tag-cible> --stat   # ~30 fichiers, pas davantage
gradlew.bat :plugins:main:compileFullReleaseKotlin
gradlew.bat :plugins:automation:testFullReleaseUnitTest
```

Un nombre de fichiers très supérieur à 30 signale qu'un conflit a été résolu
en écrasant du code amont.

### Si le rebase tourne mal

```bash
git rebase --abort
git switch backup-avant-rebase
```

### Après installation

Vérifier en priorité :
- la **progression des objectifs** (stockée par slug, elle devrait survivre)
- les **règles Automation** (le champ cible SMB est stocké par nom d'enum)
- les **réglages** de l'onglet Stats et des tranches horaires
- les **release notes** de la version cible, notamment ce qui touche au
  driver Omnipod

---

## 9. Rythme conseillé

Ce fork n'a pas vocation à suivre chaque version d'AAPS. Rebaser :

- sur les versions **correctives** touchant la boucle, la pompe ou la
  sécurité
- sur les versions **majeures**, en prenant le temps
- pas sur les versions purement cosmétiques ou de traduction

Chaque rebase coûte du temps et introduit un risque. Une version qui
fonctionne bien peut rester en place plusieurs mois.
