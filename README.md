
# HAI914I - Un moteur d’évaluation de requêtes en étoile

## Auteurs
- <federico.ulliana@inria.fr>
- Guillaume Pérution-Khili

#### Avant de commencer 

1. Cloner le projet depuis le terminal.

```
git clone https://gitlab.etu.umontpellier.fr/p00000013857/hai914i
```

2. Créer un projet dans le gitlab UM où vous pourrez déposer votre projet

Vous avez le choix de comment faire pour réaliser cette opération. Par exemple, vous pouvez changer juste l'origin du projet que vous venez de cloner (pour les locaux, rappel du cours HAI501I - Ateliers de Génie Logiciel)
```
git remote set-url origin ___indiquer_ici_l_adresse_du_depot_que_vous_venez_de_creer_sur_gitlabUM___
```

---

## Objectif
L’objectif du TD est d’implémenter l’approche hexastore pour l’interrogation des données RDF vue en cours, ainsi que les procédures nécessaires à l’évaluation de requêtes en étoile (exprimées en syntaxe SPARQL) qui seront introduites par la suite.

Le TD suivant sera dédié à l’évaluation des performances du système réalisé. Votre prototype sera comparé avec l’évaluateur de requêtes du logiciel libre InteGraal. De plus, InteGraal peut être considéré comme un système “oracle” pour vérifier la correction et la complétude de votre moteur, c’est-à-dire, vérifier que votre moteur donne toutes et seules les bonnes réponses à une requête. Celle-ci est évidemment une condition nécessaire à la conduite d’expériences.

---

## Consignes
- Le TD se fait en groupes de 1 ou 2 personnes.
- Lorsque vous avez décidé quel sera votre groupe, inscrivez-vous dans le [tableau suivant](https://docs.google.com/spreadsheets/d/1REDJjyQ0Z7sTcpBhHCWGr1PIKv6taUk9Nz_lg38reTI/edit?usp=sharing)
- Le langage de programmation est imposé : **Java**.

---

## Évaluation

Les étapes clé du projet sont sujet à la fois d'un rendu (rapport court de 3-5 pages) et d'une évaluation orale en présentiel. 
Tous les membres du groupe doivent être présents et présenter le travail réalisé à l'encadrant.

## Rendus (dans Moodle)
1. **Dictionnaire, index (Hexastore)** : rendu du code (pas de rapport) - **15 Novembre**.  
2. **Évaluation des requêtes en étoile** : code + rapport (3 pages) - **29 Novembre**.  
3. **Analyse des bancs d'essai et plan des tests à réaliser.** : déscription des tests **6 Décembre** 
4. **Analyse des performances** : rapport (5 pages) - **13 Décembre**.
5. **Document final** (10 pages + code) : **22 décembre** .

## Mini-soutenances / dates des évaluations en présentiel
1. **Dictionnaire, index (Hexastore)** : **16 Novembre**.  
2. **Évaluation des requêtes en étoile** :  **30 Novembre**.  
3. **Analyse des bancs d'essai et plan des tests à réaliser** : **6 Décembre** 
4. **Analyse des performances** :  **14 Décembre**.

Après la dernière évaluation en présentiel, vous aurez une semaine supplémentaire pour apporter les dernières corrections et finaliser au mieux votre projet. Les améliorations apportées seront naturellement prises en compte dans la note finale.


## Critères Évaluation
Les points suivants seront évalués :
- Travail réalisé (code fonctionnel, implémentation des fonctionnalités).
- Qualité du code produit (taux de couverture du code par des tests unitaires).
- Clarté et concision du rapport.
- Complétude du rapport par rapport à l'énoncé. 

---

## Point de départ du projet logiciel
Un squelette du projet est disponible dans ce dépôt git.

Vous pouvez réutiliser ce projet (ce qui est conseillé), ou juste l’étudier comme exemple. Pour s’assurer que tout fonctionne bien, exécutez le programme dans la classe **Example.java**.

### Description des principales classes fournies
- **Example.java** : montre comment lire les données et les requêtes depuis des fichiers via des parseurs dédiés.
- **RDFAtom.java** : représente des triplets RDF.
- **StarQuery.java** : représente des requêtes en étoile.
- **RDFStorage.java** : une interface décrivant le contrat (ensemble des méthodes supportées) d’un objet Hexastore.

### IDE recommandé
1. Installez **Eclipse IDE for Java Developers** :  
   [Lien Moodle](https://moodle.umontpellier.fr/mod/page/view.php?id=131074)
2. Vous pouvez aussi utiliser **IntelliJ IDEA** (version communautaire ou Ultimate avec une licence étudiante) :  
   [Télécharger IntelliJ](https://www.jetbrains.com/idea/download/?section=linux)

---

## Version de Java
Le projet utilise **Java 21**. Vous êtes libres d’utiliser une version plus récente si vous êtes à l’aise avec l’environnement Java, mais ne perdez pas de temps sur des considérations techniques.

---

## Dépendances Java avec Maven
Le projet utilise **Maven** pour la gestion des dépendances. Importez le projet dans votre IDE avec "Import Maven Project". Le fichier `pom.xml` contient toutes les bibliothèques nécessaires.

Si Eclipse ne charge pas correctement les dépendances Maven :
1. Dans **Package Explorer**, clic droit sur le projet.
2. Allez dans **Configure > Convert to Maven Project**.

### Bibliothèques externes utilisées
- **rdf4j 3.7.3** : pour lire les données RDF et les requêtes SPARQL.  
  [Documentation RDF4J](https://rdf4j.org/documentation)  
  [Javadoc RDF4J](https://rdf4j.org/javadoc/latest/)  
- **InteGraal 1.6.0** : utilisé comme base pour les implémentations et pour les comparaisons.  
  [Site InteGraal](https://rules.gitlabpages.inria.fr/integraal-website/)

---

## Tests unitaires
Des tests unitaires sont fournis dans `src/test/java`. Ils permettent de mieux comprendre le comportement attendu des objets.

### Jeu de données et requêtes
Dans le répertoire `data/`, des fichiers sont fournis pour les tests :  
- **sample_data.nt** : données de micro-tests.  
- **sample_query.queryset** : requêtes pour micro-tests.  
- **100K.nt** et **STAR_ALL_workload.queryset** : plus de données et de requêtes.

---

## Requêtes en étoile
Les requêtes en étoile sont des requêtes SPARQL composées de `n` patrons de triplets partageant une même variable.  
Exemple :  
```sparql
SELECT ?x WHERE { ?x p1 o1 . ?x p2 o2 . ?x p3 o3 }
```
Représentation graphique :
```
    -- p2 --- o2 
 ?x --- p1 --- o1 
    -- p3 --- o3 
```

### Exemples de requêtes
1. **Quels écrivains ayant reçu un prix Nobel sont aussi des peintres ?**
   ```sparql
   SELECT ?x WHERE { ?x type peintre . ?x won_prize Nobel . ?x type écrivain }
   ```
2. **Quels artistes nés en 1904 ont vécu à Paris ?**  
   Paramètres :  
   - `p1 = type, o1 = artiste`  
   - `p2 = lives_in, o2 = Paris`  
   - `p3 = birth_year, o3 = 1904`  
3. **Qui sont les amis d’Alice qui aiment le cinéma ?**
   ```sparql
   SELECT ?x WHERE { ?x friendOf Alice . ?x likes Movies }
   ```

---

## L’évaluation des requêtes

Nous identifions quatre étapes fondamentales dans l’évaluation des requêtes en étoile :

1. **Le chargement des triplets** (déjà implémenté) et leur encodage dans un dictionnaire (à implémenter).  
2. **Création de l’hexastore** avec ses indexes.  
3. **Lecture des requêtes en entrée** (en utilisant le parser de requêtes en étoile déjà implémenté).  
4. **Accès aux données et visualisation des résultats**.

---

### Important

Pour chaque étape que vous devez implémenter, vous devez au préalable écrire des tests unitaires pour chaque méthode. Suivez les étapes ci-dessous :  

1. Écrivez le résultat attendu pour différentes entrées, en traitant tous les cas limites.  
2. Rédigez le test unitaire correspondant avant d’implémenter la méthode.  
3. Utilisez ces tests pour vérifier la correction de votre implémentation.  
4. Assurez-vous que vos tests couvrent toutes les branches d’exécution de la méthode en utilisant la fonctionnalité de couverture du code (*code coverage*) de votre IDE.

---

## Étapes détaillées

### Le dictionnaire (rendu 15 Novembre)

Le dictionnaire associe un entier à chaque ressource de la base RDF, permettant un stockage compact.  
Par exemple :  
- Les triplets `<Bob, knows, Bob>` et `<1,2,1>` peuvent être vus comme équivalents avec la correspondance `{(1, Bob), (2, knows)}`.  

Le dictionnaire doit permettre d’encoder et de décoder les triplets de manière efficace.

- **Lien avec le code :** Le dictionnaire est utilisé par la méthode `add(RDFAtom a)` de l’Hexastore.

---

### Stockage Row-Store Giant-Table (rendu 15 Novembre)

Le stockage giant-table prévoit simplement de enregistrer la liste des triplets encodés dans la base.  

On vous demande d'implémenter ce stockage, en créant une classe qui implémente l'interface **RDFStorage.java**. 
L'intérêt de cette implémentation sera de disposer d'un cadre de base pour la comparation des performances par rapport à la version avec index.

- **Lien avec le code :**
  - La méthode `add(RDFAtom a)` de Giant-Table est utilisée pour l’insertion de données.  
  - La méthode `match(RDFAtom a)` de Giant-Table est utilisée pour l’interrogation des données.
---

### L’index (rendu 15 Novembre)

L’index permet une évaluation efficace des requêtes et est adapté au système de persistance choisi.  

Dans ce projet, il est demandé d’implémenter l’approche **hexastore** pour l’indexation des données.

- **Lien avec le code :**
  - La méthode `add(RDFAtom a)` de l’Hexastore est utilisée pour l’insertion de données.  
  - La méthode `match(RDFAtom a)` est utilisée pour l’interrogation des données.

- Dans votre index, on vous demande d'enregistrer des statistiques permettant de connaitre la séléctivité des patrons de triplet RDF (voir le cours). 

- La solution basique pour l'implémentation de l'index est de s'appuyer sur des `HahsMap`. Si vous rencontrez des difficultés avec Java, il s'agit de la solution à préférer. Alternativement, on vous propose d'utiliser les arbres B+ proposés par `org.mapdb.BTreeMap` ([lien](https://mapdb.org/book/btreemap/)) : cette solution solution donne lieu à un bonus et, enfin, ne diffère pas trop de l'implémentation avec `HashMap`.


---

### L’accès aux données (rendu 29 Novembre)

L’accès aux données se fait par les structures de données mises en œuvre. Une fois les solutions pour le premier patron de triplet trouvées, elles serviront à filtrer les valeurs récupérées pour les patrons suivants.

- **Lien avec le code :**  
  - Implémentez une méthode spécifique pour l’évaluation des requêtes en étoile.  
  - Cette évaluation est utilisée par la méthode `match(StarQuery q)` de l’Hexastore.

---

### Vérification de correction et complétude (rendu 29 Novembre)

Vous devez implémenter une procédure qui compare les résultats de votre système avec ceux d’InteGraal (considéré comme un "oracle"). Cela permet de vérifier la correction et la complétude de votre système, une étape fondamentale avant d’analyser ses performances.

- **Comment faire :**  
  - Utilisez `SimpleInMemoryGraphStore` pour le stockage des triplets.  
  - Convertissez une requête en étoile en requête évaluable avec la méthode `asFOQuery` de la classe `StarQuery`.

- **Exemple :** Consultez la classe `Example` dans le squelette fourni.

---

## Consignes supplémentaires

1. Implémentez l’indexation et l’accès aux données du moteur de requêtes.  
2. Les données seront stockées en mémoire vive (RAM).  
   - **Note :** Un système de persistance en mémoire secondaire peut être envisagé comme extension du projet.

---

### Lecture des entrées et export des résultats

1. Consultez le squelette du programme dans le dépôt Git. La classe `Example` montre comment lire un fichier de données.  
2. Votre système doit évaluer un ensemble de requêtes (et non une seule) sur un fichier de données unique.  
3. Les résultats doivent être exportés dans un répertoire dédié.

---
