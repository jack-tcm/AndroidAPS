# Support — problèmes rencontrés et résolutions

Journal des incidents réels, avec ce qui les a résolus. Classé par domaine.

---

## 1. Compilation

### « Conflicting declarations: val cache » — 31 puis 64 erreurs

**Cause.** Une seconde implémentation du cache avait été ajoutée dans
`StatsSummaryCalculator.kt` alors qu'une existait déjà. Doublons de `val
cache`, de `fun compute`, et références croisées cassées.

**Résolution.** Supprimer le doublon, garder la version qui inclut
`startTime` dans sa clé (elle gère le passage de minuit).

**Prévention.** Avant d'ajouter une structure transverse (cache, compteur,
état partagé), relire le fichier entier. Contrôle rapide :
```bash
grep -oE "^    (private )?(fun|val|class) [A-Za-z]+" <fichier.kt> | sort | uniq -d
```

### Le nombre d'erreurs double sans raison apparente

Le même code échoue sur `compileAapsclientDebugKotlin` **et**
`compileAapsclientReleaseKotlin` : les erreurs sont comptées deux fois. Ne
pas conclure à une aggravation.

### `:app:kspFullDebugKotlin 1 error`

Presque toujours une **conséquence** : Dagger ne peut pas générer son graphe
tant qu'un module ne compile pas. Corriger d'abord les erreurs Kotlin.

### Obtenir des messages lisibles

L'arbre replié d'Android Studio n'affiche pas les messages. Préférer :
```bash
gradlew.bat :plugins:main:compileFullReleaseKotlin 2>&1 | findstr /C:"e:" > erreurs.txt
```
Les erreurs Kotlin cascadent : les 10 premières suffisent en général.

---

## 2. Application

### Bloc noir à la place du graphique 7 j / 30 j

**Cause.** Dépassement d'entier : `var lastKept = Long.MIN_VALUE` puis
`gv.timestamp - lastKept` déborde et rend la condition toujours vraie —
aucune valeur n'était retenue, le profil sortait vide.

**Résolution.** `var lastKept: Long? = null` avec test de nullité.

**Symptôme générique.** Un graphique vide signifie « aucune donnée reçue par
la vue ». Vérifier d'abord le nombre de mesures affiché au-dessus.

### « 100 % de couverture » systématique

**Cause.** Le calcul supposait une mesure toutes les 5 min. Juggluco en
envoie une par minute, donc le ratio dépassait toujours 100 % et plafonnait.

**Résolution.** Somme des intervalles réels entre mesures, chacun plafonné à
10 min.

### Couleurs du graphique ne correspondant pas à l'écran principal

**Cause.** Utilisation de `getTargetLowMgdl` / `getTargetHighMgdl` — la
cible de l'algorithme (~115) — au lieu des repères d'affichage.

**Résolution.** `UnitDoubleKey.OverviewLowMark` / `OverviewHighMark`
(72 / 180). **Règle : toujours ces derniers pour tout affichage.**

### L'onglet Stats n'apparaît pas

Les catégories du Config Builder sont **repliées par défaut**. Menu ☰ →
Configuration → déplier **Général** avec le chevron `⌄` → cocher les deux
cases (activer + afficher en onglet).

Le plugin n'apparaît pas dans *Préférences* tant qu'il n'est pas activé.

---

## 3. Chaîne de données CGM

### AAPS ne reçoit plus les glycémies

**Résolution qui a fonctionné.** Dans xDrip+ → Paramètres inter applications :
1. **Diffusion locale** sur ON
2. **Identifier le récepteur** → `info.nightscout.androidaps`,
   **tout en minuscules** (l'autocorrection met une majuscule au « i », ce
   qui casse la réception silencieusement)
3. Forcer l'arrêt de xDrip+ **et** d'AAPS, puis relancer

**À ne pas confondre.** « API du service Broadcast » est un canal différent,
utilisé par des applications tierces (montres). Ce n'est pas celui qu'AAPS
écoute.

### Deux sources en même temps

Juggluco et xDrip+ émettent tous deux vers le même récepteur. Actifs
simultanément, AAPS reçoit des valeurs entremêlées de deux capteurs — ce qui
ressemble à du bruit capteur alors que c'est un problème de configuration.

**Règle : une seule source active à la fois.** Forcer l'arrêt de celle qui
n'est pas utilisée.

### Bascule entre capteurs

1. Terminer le capteur dans l'app sortante (Juggluco : menu → Sensor →
   *Terminate*)
2. Forcer l'arrêt de cette app
3. Démarrer le capteur dans l'app entrante
4. **Rien à changer côté AAPS** : la source reste `xDrip+` dans les deux cas

### xDrip+ perd le G6 après un mode avion

**Cause.** Tout changement d'état Bluetooth peut bloquer le scan
(« Failed to register application for bluetooth scan » dans l'écran Status).

**Résolution, par ordre :** redémarrer la session (*Start Sensor* avec le
même code) → forcer l'arrêt de xDrip+ → **redémarrer le téléphone**.
Le redémarrage est souvent le seul qui débloque vraiment.

### Fausses hypoglycémies nocturnes (compression)

Dormir sur le bras porteur écrase le capteur et fait chuter la lecture.
Avec des SMB actifs, le système corrige une hypo qui n'existe pas.

Atténuations, cumulables :
- xDrip+ → **Blocage du bruit** : xDrip+ n'envoie plus les données jugées
  bruitées au lieu de transmettre une fausse chute
- AAPS → Préférences → OpenAPS SMB → Paramètres avancés → **« Utiliser delta
  basé sur moyenne courte »** : un point isolé pèse moins dans la tendance
- alterner le bras selon le côté de sommeil habituel

### Localisation exigée par xDrip+

Contrainte d'Android, pas d'xDrip+ : le scan Bluetooth Low Energy nécessite
la permission de localisation. Sur Android 11+, l'accès en arrière-plan est
recommandé. Aucune donnée de position n'est transmise.

---

## 4. Automation et SMB

### La règle se déclenche mais aucun SMB n'est délivré

**Cause.** L'action ne modifiait que `use_smb`, l'interrupteur **maître**.
Il autorise les SMB, mais l'algorithme décide ensuite *quand* les délivrer :

| Sous-option | Sans petit-déjeuner |
|---|---|
| `enableSMB_always` | OFF |
| `enableSMB_with_COB` | pas de glucides |
| `enableSMB_after_carbs` | pas de repas récent |
| `enableSMB_with_temptarget` | pas de cible temp basse |

Aucune condition remplie → aucun SMB, quel que soit le maître.

**Résolution.** Choisir **« SMB toujours »** dans le champ *Réglage SMB* de
l'action. L'indicateur SMB de l'écran d'accueil reflète désormais les deux
réglages, ce qui rend le problème visible immédiatement.

### La règle se redéclenche toutes les 5 minutes

Comportement normal : Automation réévalue à chaque cycle tant que les
conditions restent vraies. Sans précaution, cela repousserait l'échéance
d'une durée et le retour automatique n'aurait jamais lieu — l'échéance n'est
donc armée que si aucune n'est en cours.

### Pas d'action « injecter un bolus »

C'est un choix de conception d'AAPS, pas un oubli. Les actions disponibles
sont soit informatives, soit des consignes données à la boucle, qui restent
soumises aux contraintes de sécurité.

Pour être plus offensif sur un créneau : profil basal, cible temporaire
(qui a une durée native), pourcentage de profil, ou activation SMB minutée.

---

## 5. Objectifs AAPS

### La progression survit-elle à une mise à jour ?

Oui, elle est stockée par **slug texte** (`smb`, `auto`, `autosens`…), pas
par index. Les slugs sont inchangés entre 3.4.2.3 et 3.4.2.6.

**Condition :** installation par-dessus, sans désinstaller — donc **même
keystore**. Sinon, l'export/import des réglages restaure la progression.

### Réglage introuvable dans les Préférences

Le **mode simple** masque une partie des réglages (dont maxIOB). Le
désactiver pour accéder à tout.

---

## 6. Nightscout

### Infrastructure

Auto-hébergé sur Google Cloud via l'installation scriptée **GCNS** (équipe
xDrip) : VM Compute Engine `gcns-vm`, e2-micro du palier gratuit, zone
`us-west1-c`, disque 30 Go, nom d'hôte fourni par **FreeDNS**.

### Réglages côté xDrip+

Pour éviter les doublons quand c'est AAPS qui alimente Nightscout :

- Cloud Upload → **Nightscout Sync (REST-API)** : **OFF**
- **Upload treatments** : désactivé (sinon les traitements sont comptés en
  double dans AAPS, ce qui fausse IOB et COB)
- **Back-fill data** : ne pas utiliser
- **Alert on failures** : désactivé (sinon alarme toutes les 5 min au
  moindre incident réseau)

### Incident : site très lent, quasi inaccessible (504 sur `bundle.app.js`)

**Symptôme.** Nightscout devient extrêmement lent puis quasiment
inaccessible. Le serveur répond, mais la page ne se charge pas : le
navigateur reste bloqué sur une erreur **504** en tentant de récupérer
`bundle.app.js`, le paquet JavaScript de l'interface.

**Cause.** Un problème de **droits de fichiers**, pas de ressources ni de
réseau. Nightscout tourne sous l'utilisateur `nobody`, mais le répertoire de
cache contenant le bundle appartenait à `root` : le processus ne pouvait pas
le lire, et la requête partait en timeout.

**Diagnostic — la commande à lancer en premier.** Elle vérifie directement
si l'utilisateur qui fait tourner Nightscout peut lire le bundle :

```bash
sudo -u nobody test -r /srv/nightscout-vps/node_modules/.cache/_ns_cache/public/js/bundle.app.js && echo OK || echo BLOQUE
```

`BLOQUE` confirme le problème de droits.

**Résolution.**

```bash
sudo chown -R nobody:nogroup /srv/nightscout-vps/node_modules/.cache
```

Une seule ligne. Effet immédiat, sans redémarrage.

**Quand cela peut revenir.** Le cache est régénéré par le processus qui
construit le bundle. S'il tourne en `root` — mise à jour de Nightscout,
`npm install`, reconstruction manuelle, script de déploiement lancé avec
`sudo` — le répertoire est recréé avec les droits de `root` et le problème
réapparaît à l'identique.

**Réflexe.** Après toute mise à jour ou reconstruction de Nightscout,
relancer la commande de diagnostic. Et en cas de 504 sur `bundle.app.js`,
commencer par là avant d'explorer quoi que ce soit d'autre : c'est une
vérification de dix secondes qui évite de partir sur des pistes bien plus
longues.

**Leçon de méthode.** Le diagnostic initial était parti sur une
reconstruction complète du bundle, en supposant son emplacement au lieu de
le lire dans la configuration — une demi-heure de build pour rien. Sur ce
type d'installation scriptée, **lire la configuration avant d'agir** fait
gagner plus de temps que n'importe quelle intuition.

---

## 7. Réflexes de diagnostic

**Isoler la couche avant de chercher.** Une glycémie absente dans AAPS
peut venir du capteur, de l'app CGM, du broadcast, ou d'AAPS. Regarder
d'abord si l'app CGM elle-même affiche une valeur fraîche.

**Un seul changement à la fois**, y compris pour les réglages
thérapeutiques. Modifier ISF, basale et maxIOB ensemble rend impossible de
savoir ce qui a agi.

**Vérifier après une manipulation.** Plusieurs incidents ont été découverts
longtemps après leur cause — force-stop d'une app, mode avion, changement de
source.

**Sur les réglages de doses**, ces documents décrivent le fonctionnement
technique. Le choix des valeurs se décide avec le diabétologue.
