# Guide d'utilisation — fonctions ajoutées à AAPS

## Notification avec graphique

**Rien à activer.** Déplie la notification AAPS pour voir la courbe des
3 dernières heures. Repliée, elle reste identique à l'origine.

Couleurs : vert dans la cible, jaune au-dessus de 180, rouge sous 72. Les
seuils suivent Préférences → Overview.

**Écran verrouillé** : la notification est visible sans déverrouiller. Pour
masquer la glycémie quand le téléphone est verrouillé, passer
`LOCKSCREEN_VISIBLE` à `false` dans `PersistentNotificationPlugin.kt` et
recompiler.

Pour changer la fenêtre affichée : `HOURS_TO_SHOW` dans
`NotificationGraphDrawer.kt`.

---

## Onglet Stats

### Activation

Menu ☰ → **Configuration** → déplier **Général** (chevron `⌄`) →
**Statistiques** :
- case de **gauche** = activer le plugin
- case de **droite** = afficher l'onglet en haut de l'écran

### Lecture de l'écran

**Sélecteur 24 H / 7 J / 30 J** en haut, puis flèches pour reculer dans le
temps. Les flèches décalent d'un jour, d'une semaine ou d'un mois selon la
période choisie. La flèche droite est grisée sur la période la plus récente.
Changer de période ramène au présent.

Les journées sont **calendaires** : la journée en cours va de minuit à
maintenant, donc partielle.

**Barre colorée** = temps passé bas / en cible / haut, avec les seuils
72 et 180.

**« 1282 mesures · 98 % de couverture »** — la couverture indique quelle
part de la période est réellement couverte par des mesures. En dessous de
80 %, les pourcentages perdent en fiabilité (capteur décroché, téléphone
éteint).

**Insuline et glucides** — cumul du jour en 24 h, **moyenne par jour** en
7 j et 30 j. Le libellé change en conséquence.

**Graphique** :
- en 24 h → toutes les valeurs de la journée
- en 7 j / 30 j → **profil moyen** : la ligne est la médiane heure par
  heure, la zone plus claire autour montre la dispersion. Zone large =
  journées très variables à cette heure-là. Zone étroite = schéma qui se
  répète.

C'est ce profil moyen qui révèle les phénomènes récurrents.

**Temps en cible par tranche** — quatre tranches horaires configurables.

Les valeurs indisponibles s'affichent `--`, jamais `0`.

### Réglages

Menu ☰ → **Préférences** → **Statistiques**

| Réglage | Défaut | Rôle |
|---|---|---|
| Heure de début tranche 1 à 4 | 0 / 6 / 12 / 18 | bornes des tranches |
| Lissage du graphique (minutes) | 5 | 0 = valeurs brutes, jusqu'à 30 |

Une tranche court jusqu'au début de la suivante ; la dernière rejoint la
première en passant minuit. Pour isoler une période nocturne, essayer
**23 / 2 / 7 / 12**.

Le lissage n'agit **que sur le graphique 24 h**. Les pourcentages, la
moyenne et l'HbA1c sont toujours calculés sur toutes les mesures.

### Performance

Le 30 j peut prendre quelques secondes au premier affichage — une barre de
progression apparaît. Les périodes déjà consultées sont ensuite instantanées.

---

## Action Automation « Changer SMB »

### Créer une règle

Onglet **AUTO** → menu ⋮ → **Ajouter une règle**. Après les conditions,
**AJOUT** → **Activer/désactiver SMB**.

Trois champs :

**Réglage SMB** — le champ le plus important :

- **Interrupteur maître SMB** (`use_smb`) — autorise les SMB, mais
  l'algorithme décide ensuite *quand* les délivrer. **Sans glucides
  déclarés, l'activer seul ne produit aucun SMB.**
- **SMB toujours** (`enableSMB_always`) — autorise les SMB en permanence,
  sans glucides ni cible temporaire. **C'est celui-ci qu'il faut pour agir
  à jeun.**

**Nouveau mode SMB** — ACTIVÉ ou DÉSACTIVÉ.

**Durée** — en minutes. `0` = permanent. Au-delà, l'état précédent est
restauré automatiquement.

### Exemple : effet de l'aube

```
Glycémie ≥ 110 MGDL
Et  L'heure est comprise entre 06:30 et 09:00
Et  IA est inférieur à [seuil]
→ SMB toujours : ACTIVÉ, durée 180 min
```

Les SMB s'activent au premier passage des conditions et se coupent seuls
3 heures plus tard.

Pour le seuil d'IA : regarder l'IOB affiché à l'heure visée sur plusieurs
jours. S'il est régulièrement plus haut que le seuil, la règle ne se
déclenchera jamais.

### À savoir

- **Les règles créées avant cette version restent sur l'interrupteur
  maître.** Il faut les rouvrir et choisir explicitement « SMB toujours ».
- Une règle se **redéclenche à chaque cycle** tant que ses conditions sont
  vraies — c'est normal, et l'échéance n'est pas repoussée pour autant.
- La durée fonctionne même si l'application redémarre entre-temps.
- Remettre la durée à 0 annule un retour en attente.

---

## Indicateur SMB

Dans la barre qui affiche déjà l'âge du pod, le réservoir et l'âge du
capteur.

| Affichage | Signification |
|---|---|
| **SMB** vert | disponibles à jeun |
| **SMB 2h15** vert | activation minutée, temps restant avant retour |
| **SMB** gris | indisponibles |

Il tient compte des **deux** réglages (maître et « toujours »), puisque les
deux sont nécessaires pour qu'un SMB parte sans glucides. C'est le moyen de
vérifier d'un coup d'œil qu'une règle a bien produit son effet.

---

## Mettre à jour l'application

1. **Exporter les réglages** : ⋮ → Maintenance → Export settings. Vérifier
   que le fichier est bien créé.
2. **Garder l'APK actuel** pour pouvoir revenir en arrière.
3. **Choisir le moment** : au changement de pod, IOB et COB bas, pas en
   pleine correction.
4. **Compiler** : JDK 21, variante `fullRelease`, **le même keystore** que
   l'APK installé — sinon Android refuse l'installation par-dessus, et il
   faut désinstaller (l'export de l'étape 1 devient alors indispensable).
5. **Installer par-dessus**, sans désinstaller.
6. **Vérifier** : version dans ⋮ → About, progression des objectifs,
   arrivée des glycémies, réglages OpenAPS SMB.

Rien à réactiver : les plugins et réglages sont conservés.

---

## En cas de problème

**L'onglet Stats a disparu** → Configuration → Général (déplier) → recocher
les deux cases.

**Bloc noir à la place du graphique** → aucune donnée sur la période.
Vérifier le nombre de mesures affiché juste au-dessus.

**Un réglage semble sans effet** → vérifier l'indicateur SMB et le nombre de
déclenchements dans le journal en bas de l'onglet AUTO.

**AAPS ne reçoit plus les glycémies** → dans xDrip+ : Paramètres inter
applications → Diffusion locale sur ON, et « Identifier le récepteur »
renseigné avec `info.nightscout.androidaps` (tout en minuscules). Si ça ne
suffit pas, forcer l'arrêt de xDrip+ et d'AAPS, puis relancer.

**Une seule source de glycémie à la fois** — ne jamais laisser Juggluco et
xDrip+ émettre vers AAPS simultanément : les valeurs des deux capteurs se
mélangent.
