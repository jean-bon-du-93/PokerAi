# Poker AI Project

## Objectif du Projet

Ce projet vise à développer une Intelligence Artificielle (IA) capable de jouer au poker No-Limit Texas Hold'em. L'IA est basée sur des techniques d'Apprentissage par Renforcement (Reinforcement Learning), plus spécifiquement un Deep Q-Network (DQN). Elle apprend en jouant contre elle-même (self-play) pendant de nombreuses mains.

## Architecture du Projet

Le projet est structuré en plusieurs modules Java :

1.  **Moteur de Jeu (`com.poker.engine`)**:
    *   Contient toute la logique du jeu de poker (règles, distribution des cartes, évaluation des mains, gestion des tours de mise, etc.).
    *   Ce module est indépendant de toute logique d'IA.
    *   Classes principales : `Card`, `Deck`, `Hand`, `Player`, `Action`, `GameState`, `GameEngine`.

2.  **Cœur de l'IA (`com.poker.ai`)**:
    *   Contient l'intelligence de l'agent.
    *   `PokerAIAgent`: Interface pour tous les agents IA.
    *   `StateVectorizer`: Convertit l'état du jeu (`GameState`) en un vecteur numérique pour le réseau de neurones.
    *   `DQNAgent`: Implémentation de l'agent DQN, incluant le réseau de neurones (Deeplearning4j), la mémoire de répétition, et la stratégie de décision (epsilon-greedy).

3.  **Environnement d'Entraînement (`com.poker.training`)**:
    *   Orchestre l'entraînement des agents IA par self-play.
    *   `TrainingEnvironment`: Met en place une table de jeu avec plusieurs instances de `DQNAgent`, simule des mains de poker, calcule les récompenses, et déclenche l'apprentissage des agents.

4.  **Application Principale (`com.poker.app`)**:
    *   Point d'entrée de l'application.
    *   `Main`: Permet de lancer l'application en mode entraînement (`--train`) ou en mode jeu interactif (`--play`) où un humain peut affronter l'IA.

## Prérequis

*   Java Development Kit (JDK) 17 ou supérieur.
*   Apache Maven 3.6.x ou supérieur.

## Compilation du Projet

Pour compiler le projet et créer un JAR exécutable, naviguez jusqu'à la racine du projet (où se trouve le fichier `pom.xml`) et exécutez la commande Maven suivante :

```bash
mvn clean install
```

Cela va compiler le code, exécuter les tests unitaires, et générer un fichier JAR (par exemple, `poker-ai-1.0-SNAPSHOT-jar-with-dependencies.jar`) dans le répertoire `target/`.

## Exécution de l'Application

### Mode Entraînement

Pour lancer l'entraînement des agents IA :

```bash
java -jar target/poker-ai-1.0-SNAPSHOT-jar-with-dependencies.jar --train [options]
```

Options disponibles pour le mode entraînement :
*   `--players <N>`: Nombre d'agents IA à entraîner (par défaut: 3).
*   `--hands <N>`: Nombre total de mains à jouer pendant l'entraînement (par défaut: 100000).
*   `--saveInterval <N>`: Intervalle (en nombre de mains) pour la sauvegarde des modèles d'IA (par défaut: 10000).

Exemple :
```bash
java -jar target/poker-ai-1.0-SNAPSHOT-jar-with-dependencies.jar --train --players 6 --hands 500000 --saveInterval 20000
```
Les modèles entraînés seront sauvegardés dans le répertoire racine du projet (ex: `poker_dqn_agent_Agent_1_final.zip`).

### Mode Jeu (Humain vs IA)

Pour jouer contre une ou plusieurs IA pré-entraînées :

```bash
java -jar target/poker-ai-1.0-SNAPSHOT-jar-with-dependencies.jar --play [options]
```

Options disponibles pour le mode jeu :
*   `--opponents <N>`: Nombre d'adversaires IA (par défaut: 1).
*   `--model <path>`: Chemin vers le fichier modèle de l'IA à charger. Si plusieurs opposants, cette option pourrait être étendue ou répétée (actuellement, un seul modèle est chargé pour tous les IA). (Par défaut: `poker_dqn_agent_Agent_1_final.zip`).

Exemple :
```bash
java -jar target/poker-ai-1.0-SNAPSHOT-jar-with-dependencies.jar --play --opponents 1 --model poker_dqn_agent_Agent_1_hand_50000.zip
```
Suivez les instructions dans la console pour jouer.

## Dépendances Principales

*   **Deeplearning4j (`org.deeplearning4j:deeplearning4j-core`)**: Utilisé pour la création et l'entraînement du réseau de neurones profond.
*   **ND4J (`org.nd4j:nd4j-native-platform`)**: Fournit les opérations tensorielles et les calculs numériques haute performance nécessaires à Deeplearning4j.
*   **JUnit 5 (`org.junit.jupiter:junit-jupiter-api`, `org.junit.jupiter:junit-jupiter-engine`)**: Framework de test pour les tests unitaires.
*   **SLF4J (`org.slf4j:slf4j-simple`)**: API de logging simple utilisée pour afficher les informations d'exécution et de débogage.

## Travaux Futurs et Améliorations Possibles

*   Gestion avancée des side-pots dans `GameEngine`.
*   Vectorisation d'état plus sophistiquée (ex: historique des actions plus détaillé, modélisation plus fine des adversaires).
*   Architectures de réseau de neurones plus complexes (ex: LSTM pour la séquence des actions).
*   Algorithmes d'apprentissage par renforcement alternatifs ou améliorés (ex: A2C, A3C, PPO, variantes de DQN comme Double DQN, Dueling DQN).
*   Interface utilisateur graphique (GUI) pour le mode jeu.
*   Optimisation des performances pour un entraînement plus rapide.
*   Stratégies de mise plus nuancées pour l'IA (pas seulement des types d'actions fixes).
*   Tests unitaires plus exhaustifs pour les modules IA et Training.

## Javadoc

La documentation Javadoc a été ajoutée aux classes et méthodes publiques principales à travers le code source. Pour générer la documentation complète, vous pouvez utiliser la commande Maven suivante :

```bash
mvn javadoc:javadoc
```
La documentation sera générée dans le répertoire `target/site/apidocs/`.
```
