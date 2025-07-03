package com.poker.training;

import com.poker.ai.DQNAgent;
import com.poker.ai.PokerAIAgent;
import com.poker.engine.*;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TrainingEnvironment {
    private static final Logger logger = LoggerFactory.getLogger(TrainingEnvironment.class);

    private final int numberOfPlayers;
    private final List<DQNAgent> agents;
    private final GameEngine gameEngine;
    private final double initialStack = 10000; // Stack de départ pour chaque joueur
    private final double smallBlind = 50;
    private final double bigBlind = 100;

    // Paramètres d'entraînement pour les agents DQN
    private final int replayBufferSize = 100000; // Augmenté pour plus de diversité
    private final int batchSize = 64; // Taille de batch plus grande
    private final double learningRate = 0.00025; // Taux d'apprentissage typique pour DQN
    private final double gamma = 0.99; // Facteur de décompte
    private final double initialEpsilon = 1.0;
    private final double epsilonDecay = 0.99999; // Décroissance plus lente
    private final double minEpsilon = 0.01; // Epsilon minimum

    // Pour stocker temporairement les transitions avant que la récompense ne soit connue
    private static class PendingTransition {
        String agentId;
        INDArray state;
        int actionIndex; // Index de l'action dans la liste des `possibleActionTypes` de l'agent
        GameState nextRawGameState; // Pour obtenir le INDArray nextState plus tard
        boolean done;

        PendingTransition(String agentId, INDArray state, int actionIndex, GameState nextRawGameState, boolean done) {
            this.agentId = agentId;
            this.state = state;
            this.actionIndex = actionIndex;
            this.nextRawGameState = nextRawGameState;
            this.done = done;
        }
    }

    public TrainingEnvironment(int numberOfPlayers) {
        if (numberOfPlayers < 2 || numberOfPlayers > 9) { // Typiquement 2, 6, ou 9 joueurs
            throw new IllegalArgumentException("Number of players must be between 2 and 9.");
        }
        this.numberOfPlayers = numberOfPlayers;
        this.agents = new ArrayList<>();
        List<Player> initialEnginePlayers = new ArrayList<>();

        // Définir les types d'actions que l'IA peut choisir. C'est crucial.
        // FOLD, CALL, RAISE (avec différents montants)
        // Par exemple: FOLD, CALL, RAISE_MIN, RAISE_HALF_POT, RAISE_POT, RAISE_ALL_IN
        // Pour l'instant, utilisons les types de base, DQNAgent les interprétera.
        List<Action.ActionType> agentActionTypes = List.of(
                Action.ActionType.FOLD,
                Action.ActionType.CHECK, // CHECK et CALL seront souvent contextuels
                Action.ActionType.CALL,
                Action.ActionType.BET,   // Représente une mise (ex: BB, 1/2 pot, pot)
                Action.ActionType.RAISE  // Représente une relance (ex: min, pot, all-in)
        );
        // Le DQNAgent va mapper ces 5 types à ses sorties.
        // La logique dans DQNAgent.createActionFromTypeAndGameState et getBestLegalActionIndex doit être robuste.

        StateVectorizer dummyVectorizer = new StateVectorizer(numberOfPlayers); // Pour obtenir inputSize

        for (int i = 0; i < numberOfPlayers; i++) {
            String agentId = "Agent_" + (i + 1);
            DQNAgent agent = new DQNAgent(
                    agentId,
                    dummyVectorizer.getVectorSize(),
                    agentActionTypes, // Les types d'actions que l'agent peut choisir
                    replayBufferSize,
                    batchSize,
                    learningRate,
                    gamma,
                    initialEpsilon,
                    epsilonDecay,
                    minEpsilon,
                    numberOfPlayers
            );
            agent.setInitialStackForNormalization(initialStack);
            this.agents.add(agent);
            initialEnginePlayers.add(new Player(agentId, initialStack)); // L'ID du Player doit correspondre à l'agentId
        }

        this.gameEngine = new GameEngine(initialEnginePlayers, smallBlind, bigBlind);
    }

    public void train(int numberOfHandsToPlay, int saveInterval) {
        logger.info("Starting training for {} hands...", numberOfHandsToPlay);
        long totalTrainingStartTime = System.currentTimeMillis();

        for (int handNumber = 1; handNumber <= numberOfHandsToPlay; handNumber++) {
            long handStartTime = System.currentTimeMillis();
            logger.info("--- Starting Hand #{} ---", handNumber);
            if (handNumber % 100 == 0) { // Log Epsilon périodiquement
                 logger.info("Current Epsilon for Agent_1: {}", String.format("%.4f", agents.get(0).getEpsilon()));
            }


            // Stocker les stacks avant la main pour calculer les récompenses
            Map<String, Double> stacksBeforeHand = new HashMap<>();
            for (DQNAgent agent : agents) {
                // Trouver le joueur correspondant dans GameEngine pour obtenir le stack actuel
                // (car les agents peuvent être éliminés et recréés, ou leurs stacks peuvent changer)
                // Pour cette boucle, on prendra les stacks depuis le GameState au début de la main.
            }

            GameState currentGameState = gameEngine.startNewHand();
            if (currentGameState.isHandOver() && currentGameState.getPlayers().size() < 2) {
                logger.warn("Not enough players to continue training. Stopping.");
                break; // Fin de la partie si pas assez de joueurs
            }


            // Mettre à jour les stacks initiaux pour la normalisation si nécessaire (ex: si les joueurs changent)
            // Ici, on suppose que les stacks initiaux sont constants pour la normalisation.
            // Mais les stacks réels des joueurs changent.
            for (Player p : currentGameState.getPlayers()) {
                 stacksBeforeHand.put(p.getId(), p.getStack());
                 // Assurer que l'agent correspondant a le bon ID.
                 DQNAgent currentAgentForStack = agents.stream().filter(a -> a.getName().equals(p.getId())).findFirst().orElse(null);
                 if(currentAgentForStack != null) {
                    // currentAgentForStack.setInitialStackForNormalization(initialStack); // On utilise un stack de référence fixe.
                 }
            }


            List<PendingTransition> handTransitions = new ArrayList<>();
            int decisionNodesThisHand = 0;

            while (!currentGameState.isHandOver()) {
                decisionNodesThisHand++;
                Player currentPlayerDetails = currentGameState.getCurrentPlayer();
                if (currentPlayerDetails == null) {
                    logger.warn("No current player in GameState, but hand not over. State: {}", currentGameState);
                    break; // Devrait être géré par GameEngine, mais sécurité
                }

                String currentAgentId = currentPlayerDetails.getId();
                DQNAgent actingAgent = agents.stream()
                        .filter(a -> a.getName().equals(currentAgentId))
                        .findFirst()
                        .orElse(null);

                if (actingAgent == null) {
                    throw new IllegalStateException("No DQNAgent found for current player ID: " + currentAgentId);
                }

                List<Action> legalActions = gameEngine.determineLegalActions(currentPlayerDetails, currentGameState);
                if (legalActions.isEmpty()) {
                     logger.warn("Agent {} (Player {}) has no legal actions. State: {}", actingAgent.getName(), currentPlayerDetails.getName(), currentGameState);
                     // Cela peut arriver si le joueur est all-in et que l'action passe, ou si GameEngine a un bug.
                     // On force une avancée ou on considère la main terminée.
                     // Normalement, GameEngine ne devrait pas demander d'action si aucune n'est possible.
                     // Si on est ici, c'est que le GameEngine a renvoyé un joueur actif.
                     break;
                }

                // Nécessaire pour les helpers internes de DQNAgent.chooseAction (le hack temporaire)
                // DQNAgent.setCurrentGameStateForHelpers(currentGameState); // Plus besoin avec refactor

                INDArray stateVector = actingAgent.stateVectorizer.vectorizeState(currentGameState, currentAgentId, initialStack, bigBlind);
                Action chosenAction = actingAgent.chooseAction(currentGameState, legalActions);

                // Trouver l'index de l'action choisie par rapport à `possibleActionTypes` de l'agent
                int chosenActionIndex = -1;
                // DQNAgent.createActionFromTypeAndGameState nous donne l'action avec le montant.
                // On doit trouver à quel type de base cela correspond.
                Action.ActionType baseChosenType = chosenAction.getType();
                // Si chosenAction est un BET de 50, et que nos types sont BET_MIN, BET_POT,
                // il faut une logique pour mapper. Pour l'instant, on prend le type direct.
                for(int i=0; i < actingAgent.possibleActionTypes.size(); ++i) {
                    if (actingAgent.possibleActionTypes.get(i) == baseChosenType) {
                        // Pour BET/RAISE, il faudrait idéalement mapper au "concept" d'action (ex: RAISE_POT)
                        // plutôt qu'au type générique.
                        // C'est une limitation si plusieurs sorties du réseau mappent au même ActionType.
                        // Supposons pour l'instant un mapping simple.
                        chosenActionIndex = i;
                        break;
                    }
                }
                if (chosenActionIndex == -1) {
                    // Si le type de l'action choisie n'est pas dans `possibleActionTypes` (devrait pas arriver si bien configuré)
                    logger.error("Chosen action type {} not found in agent's possibleActionTypes. Defaulting index.", chosenAction.getType());
                    // Tenter de trouver un FOLD ou le premier type.
                    for(int i=0; i < actingAgent.possibleActionTypes.size(); ++i) {
                         if (actingAgent.possibleActionTypes.get(i) == Action.ActionType.FOLD) chosenActionIndex = i;
                    }
                    if (chosenActionIndex == -1) chosenActionIndex = 0;
                }


                GameState previousGameState = currentGameState; // Sauvegarder pour le log
                currentGameState = gameEngine.handleAction(chosenAction);

                // Stocker la transition (sans la récompense finale pour l'instant)
                // Le nextStateVector sera calculé à la fin de la main ou avant le prochain trainBatch.
                handTransitions.add(new PendingTransition(currentAgentId, stateVector, chosenActionIndex, currentGameState, currentGameState.isHandOver()));

                if (logger.isTraceEnabled()) {
                     logger.trace("Hand #{}, Agent: {}, Action: {}, PrevPot: {}, NewPot: {}, PrevStack: {}, NewStack: {}",
                                 handNumber, actingAgent.getName(), chosenAction,
                                 previousGameState.getPotSize(), currentGameState.getPotSize(),
                                 previousGameState.getPlayers().stream().filter(p->p.getId().equals(currentAgentId)).findFirst().get().getStack(),
                                 currentGameState.getPlayers().stream().filter(p->p.getId().equals(currentAgentId)).findFirst().get().getStack()
                                 );
                }
            }

            // Fin de la main: calculer les récompenses et compléter les transitions
            Map<String, Double> stacksAfterHand = new HashMap<>();
            for (Player p : currentGameState.getPlayers()) {
                stacksAfterHand.put(p.getId(), p.getStack());
            }

            for (PendingTransition pt : handTransitions) {
                DQNAgent agentForTransition = agents.stream().filter(a -> a.getName().equals(pt.agentId)).findFirst().get();

                double reward = (stacksAfterHand.getOrDefault(pt.agentId, initialStack) - stacksBeforeHand.getOrDefault(pt.agentId, initialStack)) / bigBlind; // Normaliser par BB

                // Le nextState pour la transition est l'état qui a suivi l'action de CETTE transition.
                // Ce nextState a été stocké dans pt.nextRawGameState.
                INDArray nextStateVector;
                if (pt.done) { // Si c'était la dernière action menant à la fin de la main
                    nextStateVector = Nd4j.zeros(1, agentForTransition.stateVectorizer.getVectorSize()); // État terminal
                } else {
                    // Le pt.nextRawGameState est l'état vu par le *prochain* joueur.
                    // Pour la perspective de l'agent de la transition 'pt', le nextState est cet état là.
                    nextStateVector = agentForTransition.stateVectorizer.vectorizeState(pt.nextRawGameState, pt.agentId, initialStack, bigBlind);
                }

                agentForTransition.observeTransition(pt.state, pt.actionIndex, reward, nextStateVector, pt.done);

                // Appeler handComplete pour l'agent (même si la récompense est aussi dans la transition)
                // C'est redondant si l'agent utilise la transition, mais peut être utile pour d'autres logiques.
                if (pt.done && pt.nextRawGameState.getWinners() != null && pt.nextRawGameState.getWinners().stream().anyMatch(w -> w.getId().equals(pt.agentId))) {
                    // Si l'agent a gagné à la fin de cette transition
                }
                 agentForTransition.handComplete(pt.nextRawGameState, reward); // L'agent peut utiliser ça pour log ou autre.

            }

            // Entraîner chaque agent
            for (DQNAgent agent : agents) {
                agent.trainBatch();
            }

            long handEndTime = System.currentTimeMillis();
            logger.info("Hand #{} finished. Winners: {}. Winning Hand: {}. Decisions: {}. Duration: {} ms. Pot: {}",
                        handNumber,
                        currentGameState.getWinners() != null ? currentGameState.getWinners().stream().map(Player::getName).collect(Collectors.joining(",")) : "N/A",
                        currentGameState.getWinningHand() != null ? currentGameState.getWinningHand().getHandType() : "N/A",
                        decisionNodesThisHand,
                        (handEndTime - handStartTime),
                        String.format("%.2f", currentGameState.getPotSize()));


            // Sauvegarder les modèles périodiquement
            if (handNumber % saveInterval == 0) {
                for (DQNAgent agent : agents) {
                    try {
                        agent.saveModel("poker_dqn_agent_" + agent.getName() + "_hand_" + handNumber + ".zip");
                    } catch (IOException e) {
                        logger.error("Error saving model for agent " + agent.getName(), e);
                    }
                }
                logger.info("Models saved at hand #{}", handNumber);
            }

            // Vérifier si on doit arrêter l'entraînement (ex: un seul joueur avec des jetons)
            long playersWithChips = currentGameState.getPlayers().stream().filter(p -> p.getStack() > 0).count();
            if (playersWithChips < 2 && numberOfHandsToPlay > handNumber) {
                logger.info("Only {} player(s) with chips remaining. Ending training early.", playersWithChips);
                break;
            }

        }
        long totalTrainingEndTime = System.currentTimeMillis();
        logger.info("Training finished. Total duration: {} seconds.", (totalTrainingEndTime - totalTrainingStartTime) / 1000);

        // Sauvegarde finale des modèles
        for (DQNAgent agent : agents) {
            try {
                agent.saveModel("poker_dqn_agent_" + agent.getName() + "_final.zip");
            } catch (IOException e) {
                logger.error("Error saving final model for agent " + agent.getName(), e);
            }
        }
        logger.info("Final models saved.");
    }

    public List<DQNAgent> getAgents() {
        return agents;
    }

    public static void main(String[] args) {
        int numPlayers = 3; // ou 6
        int numHands = 100000; // Nombre de mains pour l'entraînement
        int saveModelInterval = 10000; // Sauvegarder tous les X mains

        logger.info("Initializing Training Environment with {} players.", numPlayers);
        TrainingEnvironment trainingEnv = new TrainingEnvironment(numPlayers);

        // Optionnel: charger des modèles pré-entraînés s'ils existent
        // for (DQNAgent agent : trainingEnv.getAgents()) {
        //     try {
        //         agent.loadModel("poker_dqn_agent_" + agent.getName() + "_final.zip"); // Charger le dernier modèle sauvegardé
        //     } catch (IOException e) {
        //         logger.warn("Could not load pre-trained model for {}, starting from scratch.", agent.getName());
        //     }
        // }

        trainingEnv.train(numHands, saveModelInterval);
    }
}
