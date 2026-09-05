# Ce qui a été ajouté à l'application — explication simple

Document destiné à quelqu'un qui n'est pas informaticien : un proche, un
soignant, ou toute personne qui se demande ce qui a été modifié et si c'est
sérieux.

## De quoi parle-t-on

AAPS est une application libre et gratuite qui pilote une pompe à insuline.
Elle lit la glycémie envoyée par un capteur, et ajuste l'insuline en continu
sans intervention. Son code source est public : n'importe qui peut le lire,
et le modifier pour son propre usage.

C'est ce qui a été fait ici. Quatre ajouts, tous liés à **l'affichage** :
montrer plus clairement ce que l'application fait déjà, et éviter des
manipulations répétitives.

## Les quatre ajouts

### 1. Un graphique dans la notification

Avant, la notification permanente affichait la glycémie sous forme de
chiffres. Maintenant, en la dépliant, on voit la **courbe des trois
dernières heures** : on saisit d'un coup d'œil si ça monte, descend, ou
reste stable — sans ouvrir l'application.

Elle est aussi visible sur l'écran verrouillé. C'est pratique, mais ça
signifie que la glycémie est lisible par toute personne qui regarde le
téléphone. Ce comportement peut être désactivé.

### 2. Un onglet « Statistiques »

Un nouvel écran qui répond à « comment ça s'est passé cette semaine ? ».
Il affiche, sur 24 heures, 7 jours ou 30 jours :

- le **temps passé dans la bonne fourchette** de glycémie
- la moyenne, et une estimation de l'hémoglobine glyquée
- la quantité d'insuline et de glucides
- un graphique, et le découpage par tranches horaires

L'intérêt principal : repérer des **schémas qui se répètent**. C'est ainsi
qu'a été confirmée une montée systématique de la glycémie en fin de nuit —
un phénomène connu, appelé effet de l'aube.

### 3. Un réglage automatique limité dans le temps

L'application permet déjà de programmer des règles du type « si telle
condition, alors telle action ». Il manquait la possibilité de dire
« ...pendant trois heures, puis reviens comme avant ».

Sans ça, il fallait créer une deuxième règle pour annuler la première, et
espérer qu'elle se déclenche bien. Maintenant le retour est automatique, et
il fonctionne même si l'application redémarre entre-temps.

### 4. Un voyant d'état

Un petit indicateur sur l'écran principal montre si une fonction est
active, et pour combien de temps. Il a été ajouté après avoir constaté
qu'un réglage semblait activé alors qu'il ne produisait aucun effet — sans
qu'aucun écran ne le signale.

## Est-ce que ça touche à la sécurité ?

**Non, et c'est un point sur lequel il y a eu une vigilance constante.**

Les trois premiers ajouts ne font que **lire** des informations pour les
afficher. Ils ne décident jamais d'une dose, ne commandent jamais la pompe,
et ne modifient aucune limite de sécurité.

Le quatrième modifie un réglage — mais un réglage qui existait déjà et qui
est modifiable à la main dans l'application. Le seul changement est qu'il
peut désormais revenir tout seul à son état précédent. Autrement dit, il
**limite** un effet au lieu de l'étendre.

Toutes les protections de l'application restent en place : plafonds de dose,
arrêt automatique si la glycémie descend trop, et le parcours de validation
obligatoire avant de débloquer les fonctions avancées.

À plusieurs reprises, des modifications visant à contourner ces protections
ont été envisagées, puis écartées. Elles n'ont pas été faites.

## Les limites à connaître

**Ce n'est pas un logiciel médical certifié**, ni avant ni après ces
modifications. AAPS est utilisé sous la responsabilité de la personne qui
l'installe, ce qui était déjà le cas.

**Ces ajouts n'ont pas été relus par d'autres personnes.** Ils ne font pas
partie de la version officielle et ne sont installés que sur ce téléphone.

**Ils ont été écrits avec l'aide d'une IA**, qui n'a pas pu les tester
elle-même : c'est l'utilisateur qui compile et vérifie que tout fonctionne.
Plusieurs erreurs ont d'ailleurs été détectées de cette façon, puis
corrigées.

**Les décisions médicales restent médicales.** Ces outils aident à voir ce
qui se passe ; les ajustements de doses se décident avec le diabétologue.
